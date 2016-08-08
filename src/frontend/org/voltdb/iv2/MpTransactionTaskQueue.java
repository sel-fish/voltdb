/* This file is part of VoltDB.
 * Copyright (C) 2008-2016 VoltDB Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb.iv2;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.voltcore.logging.VoltLogger;
import org.voltdb.CatalogContext;
import org.voltdb.CatalogSpecificPlanner;
import org.voltdb.exceptions.TransactionRestartException;
import org.voltdb.messaging.FragmentResponseMessage;
import org.voltdb.messaging.FragmentTaskMessage;

import com.google_voltpatches.common.collect.HashMultimap;
import com.google_voltpatches.common.collect.Maps;

/**
 * Provide an implementation of the TransactionTaskQueue specifically for the MPI.
 * This class will manage separating the stream of reads and writes to different
 * Sites and block appropriately so that reads and writes never execute concurrently.
 */
public class MpTransactionTaskQueue extends TransactionTaskQueue
{
    protected static final VoltLogger tmLog = new VoltLogger("TM");

    // Track the current writes and reads in progress.  If writes contains anything, reads must be empty,
    // and vice versa
    private final Map<Long, TransactionTask> m_currentWrites = new HashMap<Long, TransactionTask>();
    private final Map<Long, TransactionTask> m_currentReads = new HashMap<Long, TransactionTask>();
    private Deque<TransactionTask> m_backlog = new ArrayDeque<TransactionTask>();
    private final HashMultimap<Integer, Long> m_currentWriteSites = HashMultimap.create();
    private final HashMultimap<Integer, Long> m_currentReadSites = HashMultimap.create();

    private MpSitePool m_sitePool = null;
    private MpSitePool m_updateSitePool = null;

    private final Map<Integer, Long> m_partitionMastersHSIDs;

    MpTransactionTaskQueue(SiteTaskerQueue queue, long initialTnxId)
    {
        super(queue, initialTnxId);
        m_partitionMastersHSIDs = Maps.newHashMap();
    }

    void setMpRoSitePool(MpSitePool sitePool)
    {
        m_sitePool = sitePool;
    }

    void setMpUpdateSitePool(MpSitePool sitePool)
    {
        m_updateSitePool = sitePool;
    }

    synchronized void updateCatalog(String diffCmds, CatalogContext context, CatalogSpecificPlanner csp)
    {
        m_sitePool.updateCatalog(diffCmds, context, csp);
        m_updateSitePool.updateCatalog(diffCmds, context, csp);
    }

    void shutdown()
    {
        if (m_sitePool != null) {
            m_sitePool.shutdown();
        }

        if (m_updateSitePool != null) {
            m_updateSitePool.shutdown();
        }

    }

    /**
     * Stick this task in the backlog.
     * Many network threads may be racing to reach here, synchronize to
     * serialize queue order.
     * Always returns true in this case, side effect of extending
     * TransactionTaskQueue.
     */
    @Override
    synchronized boolean offer(TransactionTask task)
    {
        Iv2Trace.logTransactionTaskQueueOffer(task);
        m_backlog.addLast(task);
        taskQueueOffer();
        return true;
    }

    // XXX Fixme for N part txns...may have both MP reads and writes concurrently
    // repair is used by MPI repair to inject a repair task into the
    // SiteTaskerQueue.  Before it does this, it unblocks the MP transaction
    // that may be running in the Site thread and causes it to rollback by
    // faking an unsuccessful FragmentResponseMessage.
    synchronized void repair(SiteTasker task, List<Long> masters, Map<Integer, Long> partitionMasters)
    {
        // We know that every Site assigned to the MPI (either the main writer or
        // any of the MP read pool) will only have one active transaction at a time,
        // and that we either have active reads or active writes, but never both.
        // Figure out which we're doing, and then poison all of the appropriate sites.
        Map<Long, TransactionTask> currentSet;
        if (!m_currentReads.isEmpty()) {
            assert(m_currentWrites.isEmpty());
            tmLog.debug("MpTTQ: repairing reads");
            for (Long txnId : m_currentReads.keySet()) {
                m_sitePool.repair(txnId, task);
            }
            currentSet = m_currentReads;
        }
        else {
            tmLog.debug("MpTTQ: repairing writes");
            m_taskQueue.offer(task);
            currentSet = m_currentWrites;
        }
        for (Entry<Long, TransactionTask> e : currentSet.entrySet()) {
            if (e.getValue() instanceof MpProcedureTask) {
                MpProcedureTask next = (MpProcedureTask)e.getValue();
                tmLog.debug("MpTTQ: poisoning task: " + next);
                next.doRestart(masters, partitionMasters);
                MpTransactionState txn = (MpTransactionState)next.getTransactionState();
                // inject poison pill
                FragmentTaskMessage dummy = new FragmentTaskMessage(0L, 0L, 0L, 0L, false, false, false);
                FragmentResponseMessage poison =
                    new FragmentResponseMessage(dummy, 0L); // Don't care about source HSID here
                // Provide a TransactionRestartException which will be converted
                // into a ClientResponse.RESTART, so that the MpProcedureTask can
                // detect the restart and take the appropriate actions.
                TransactionRestartException restart = new TransactionRestartException(
                        "Transaction being restarted due to fault recovery or shutdown.", next.getTxnId());
                poison.setStatus(FragmentResponseMessage.UNEXPECTED_ERROR, restart);
                txn.offerReceivedFragmentResponse(poison);
            }
            else {
                // Don't think that EveryPartitionTasks need to do anything here, since they
                // don't actually run java, they just exist for sequencing.  Any cleanup should be
                // to the duplicate counter in MpScheduler for this transaction.
            }
        }
        // Now, iterate through the backlog and update the partition masters
        // for all ProcedureTasks
        Iterator<TransactionTask> iter = m_backlog.iterator();
        while (iter.hasNext()) {
            TransactionTask tt = iter.next();
            if (tt instanceof MpProcedureTask) {
                MpProcedureTask next = (MpProcedureTask)tt;
                tmLog.debug("Repair updating task: " + next + " with masters: " + masters);
                next.updateMasters(masters, partitionMasters);
            }
            else if (tt instanceof EveryPartitionTask) {
                EveryPartitionTask next = (EveryPartitionTask)tt;
                tmLog.debug("Repair updating EPT task: " + next + " with masters: " + masters);
                next.updateMasters(masters);
            }
        }
    }

    private void taskQueueOffer(TransactionTask task)
    {
        Iv2Trace.logSiteTaskerQueueOffer(task);
        if (task.getTransactionState().isReadOnly()) {
            m_sitePool.doWork(task.getTxnId(), task);
        }
        else {
            // UpdateSitePool does not support all procedure types, so only send N-part transactions there
            if(task.getTransactionState().getInvocation() != null &&
                    task.getTransactionState().getInvocation().getProcName().equals("@AdHoc_NP")) {
                m_updateSitePool.doWork(task.getTxnId(), task);
            } else {
                m_taskQueue.offer(task);
            }
        }
    }

    void updatePartitionMasters(final Map<Integer, Long> partitionMasters) {
        m_partitionMastersHSIDs.clear();
        m_partitionMastersHSIDs.putAll(partitionMasters);
    }

    private boolean hasReadSiteConflicts(TransactionTask task) {
        HashMultimap<Integer,Long> currentReadSitesCopy = HashMultimap.create(m_currentReadSites);
        if (task instanceof MpProcedureTask) {
            currentReadSitesCopy.keySet().retainAll(((MpTransactionState) task.getTransactionState()).getMasterHSIds().keySet());
        }
        return !currentReadSitesCopy.isEmpty();
    }

    private boolean hasWriteSiteConflicts(TransactionTask task) {
        HashMultimap<Integer,Long> currentWriteSitesCopy = HashMultimap.create(m_currentWriteSites);
        if (task instanceof MpProcedureTask) {
            currentWriteSitesCopy.keySet().retainAll(((MpTransactionState) task.getTransactionState()).getMasterHSIds().keySet());
        }

        return !currentWriteSitesCopy.isEmpty();
    }

    private boolean taskQueueOffer()
    {
        // Do we have something to do?
        // - If so, is it a write?
        //   - If so, are there reads or writes outstanding?
        //     - if not, pull it from the backlog, add it to current write set, and queue it
        //     - if so, bail for now
        //   - If not, are there writes outstanding?
        //     - if not, while there are reads on the backlog and the pool has capacity:
        //       - pull the read from the backlog, add it to the current read set, and queue it.
        //       - bail when done
        //     - if so, bail for now

        boolean retval = false;
        if (!m_backlog.isEmpty()) {
            // We may not queue the next task, just peek to get the read-only state
            TransactionTask task = m_backlog.peekFirst();

            while (task != null &&
                    ((!task.getTransactionState().isReadOnly() && !hasReadSiteConflicts(task) && !hasWriteSiteConflicts(task) && m_updateSitePool.canAcceptWork()) ||
                    (task.getTransactionState().isReadOnly() && !hasWriteSiteConflicts(task) && m_sitePool.canAcceptWork()))
                    ) {
                task = m_backlog.pollFirst();
                if (!task.getTransactionState().isReadOnly()) {

                    m_currentWrites.put(task.getTxnId(), task);
                    if (task instanceof MpProcedureTask) {
                        for (Entry<Integer, Long> entry : ((MpTransactionState) task.getTransactionState()).getMasterHSIds().entrySet()) {
                            m_currentWriteSites.put(entry.getKey(), task.getTxnId());
                        }
                    }
                    else {
                        // Reserve every partition
                        for (Entry<Integer, Long> entry : m_partitionMastersHSIDs.entrySet()) {
                            m_currentWriteSites.put(entry.getKey(), task.getTxnId());
                        }
                    }

                    taskQueueOffer(task);
                    retval = true;
                }
                else {

                    assert(task.getTransactionState().isReadOnly());
                    m_currentReads.put(task.getTxnId(), task);
                    if (task instanceof MpProcedureTask) {
                        for (Entry<Integer, Long> entry : ((MpTransactionState) task.getTransactionState()).getMasterHSIds().entrySet()) {
                            m_currentReadSites.put(entry.getKey(), task.getTxnId());
                        }
                    }
                    else {
                        // Reserve every partition
                        for (Entry<Integer, Long> entry : m_partitionMastersHSIDs.entrySet()) {
                            m_currentReadSites.put(entry.getKey(), task.getTxnId());
                        }
                    }
                    taskQueueOffer(task);
                    retval = true;

                }
                // Prime the pump with the head task, if any.  If empty,
                // task will be null
                task = m_backlog.peekFirst();

            }

        }
        return retval;
    }

    /**
     * Indicate that the transaction associated with txnId is complete.  Perform
     * management of reads/writes in progress then call taskQueueOffer() to
     * submit additional tasks to be done, determined by whatever the current state is.
     * See giant comment at top of taskQueueOffer() for what happens.
     */
    @Override
    synchronized int flush(long txnId)
    {
        int offered = 0;
        if (m_currentReads.containsKey(txnId)) {
            TransactionTask task = m_currentReads.get(txnId);
            Iterator<Integer> iter;
            if (task instanceof MpProcedureTask) {
                iter = ((MpTransactionState) task.getTransactionState()).getMasterHSIds().keySet().iterator();
            }
            else {
                iter = m_partitionMastersHSIDs.keySet().iterator();
            }
            while(iter.hasNext()) {
                m_currentReadSites.remove(iter.next(),txnId);
            }
            m_currentReads.remove(txnId);
            m_sitePool.completeWork(txnId);
        }
        else {
            assert(m_currentWrites.containsKey(txnId));
            TransactionTask task = m_currentWrites.get(txnId);
            Iterator<Integer> iter;
            if (task instanceof MpProcedureTask) {
                iter = ((MpTransactionState) task.getTransactionState()).getMasterHSIds().keySet().iterator();
            }
            else {
                iter = m_partitionMastersHSIDs.keySet().iterator();
            }
            while(iter.hasNext()) {
                m_currentWriteSites.remove(iter.next(),txnId);
            }
            m_currentWrites.remove(txnId);
            if (task.getTransactionState().getInvocation().getProcName().equals("@AdHoc_NP")) {
                m_updateSitePool.completeWork(txnId);
            }
        }
        if (taskQueueOffer()) {
            ++offered;
        }
        return offered;
    }

    /**
     * Restart the current task at the head of the queue.  This will be called
     * instead of flush by the currently blocking MP transaction in the event a
     * restart is necessary.
     */
    @Override
    synchronized void restart()
    {
        if (!m_currentReads.isEmpty()) {
            // re-submit all the tasks in the current read set to the pool.
            // the pool will ensure that things submitted with the same
            // txnID will go to the the MpRoSite which is currently running it
            for (TransactionTask task : m_currentReads.values()) {
                taskQueueOffer(task);
            }
        }
        else {
            assert(!m_currentWrites.isEmpty());
            TransactionTask task;
            // There currently should only ever be one current write.  This
            // is the awkward way to get a single value out of a Map
            task = m_currentWrites.entrySet().iterator().next().getValue();
            taskQueueOffer(task);
        }
    }

    /**
     * How many Tasks are un-runnable?
     * @return
     */
    @Override
    synchronized int size()
    {
        return m_backlog.size();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("MpTransactionTaskQueue:").append("\n");
        sb.append("\tSIZE: ").append(m_backlog.size()).append("\n");
        if (!m_backlog.isEmpty()) {
            sb.append("\tHEAD: ").append(m_backlog.getFirst()).append("\n");
        }
        return sb.toString();
    }
}
