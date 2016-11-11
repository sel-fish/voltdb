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

package org.voltdb.client;

/**
 * Listener that a client application can provide to a {@link Client} in order to receive notifications
 * when certain events occur, such as backpressure or connection issues.
 *
 * To use this class, implement one or more methods in a subclass and set it in your {@link ClientConfig}
 * object.
 */
public class ClientStatusListenerExt {

    /**
     * Cause of the disconnection event.
     */
    public static enum DisconnectCause {
        /** Connection timed out */
        TIMEOUT,
        /** Connection closed by other side */
        CONNECTION_CLOSED
    }

    /**
     * Cause of the connection creation failure.
     */
    public static enum ConnectionCause {
        /** connection is timed out */
        CONNECTION_TIMEOUT,
        /**host unknown*/
        HOST_UNKNOWN,
        /** Connection closed by other side */
        SERVER_UNAVAILABLE,
        /**system procedure failure*/
        TOPOLOY_QUERY_FAILURE;

        /**
         * convert ClientResponse status to ConnectionCause
         * @param clientResponseStatus
         * @return ConnectionCause
         */
        static ConnectionCause toConnectionCreationCause(byte clientResponseStatus) {
            ConnectionCause cause = TOPOLOY_QUERY_FAILURE;
            if (clientResponseStatus == ClientResponse.CONNECTION_LOST || clientResponseStatus == ClientResponse.CONNECTION_TIMEOUT) {
                cause = CONNECTION_TIMEOUT;
            } else if (clientResponseStatus == ClientResponse.SERVER_UNAVAILABLE) {
                cause = SERVER_UNAVAILABLE;
            }
            return cause;
        }
    }

    /**
     * Notify listeners that a connection to a host was lost.
     * @param hostname Name of the host the connection was lost from.
     * @param port Port number of the connection to the lost host.
     * @param connectionsLeft Number of remaining connections this client has to the DB
     * @param cause The reason why this callback is being called
     */
    public void connectionLost(String hostname, int port, int connectionsLeft, DisconnectCause cause) {}

    /**
     * Notify listeners that the client tried to create connection to unconnected nodes upon topology change.
     * When both client affinity and topology aware are enabled, connections to newly joined nodes or other unconnected nodes
     * within the cluster will be created. This method will be invoked to notify the client upon a new connection is created.
     * Client can override this method to keep track of the connections
     * @param hostname Name of the host the connection was created.
     * @param ip  The IP address
     * @param port Port number of the connection to the node.
     * @param cause The creation failure cause
     */
    public void connectionCreated(String hostname, String ip, int port, ConnectionCause cause) {}

    /**
     * Called by the client API whenever backpressure starts/stops. Backpressure is a condition
     * where all TCP connections to the servers are full and the {@link Client} will no longer
     * queue invocations.
     * @param status <code>true</code> if there is backpressure and <code>false</code> otherwise.
     */
    public void backpressure(boolean status) {}

    /**
     * Called when a {@link ProcedureCallback#clientCallback(ClientResponse)} invocation throws
     * an exception.
     * @param callback The callback that threw an exception.
     * @param r The response object passed to the callback.
     * @param e The exception thrown by the callback.
     */
    public void uncaughtException(ProcedureCallback callback, ClientResponse r, Throwable e) {}

    /**
     * Called when a response arrives on a connection that has already had its callback called
     * due to an elapsed timeout.
     *
     * @param r The late response received.
     * @param hostname The hostname or ip given at connection create time.
     * @param port The port given at connection create time.
     */
    public void lateProcedureResponse(ClientResponse r, String hostname, int port) {}
}
