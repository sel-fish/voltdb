-- Tell sqlcmd to batch the following commands together,
-- so that the schema loads quickly.
file -inlinebatch END_OF_BATCH

CREATE TABLE WAREHOUSE (
  W_ID SMALLINT DEFAULT '0' NOT NULL,
  W_NAME VARCHAR(16) DEFAULT NULL,
  W_STREET_1 VARCHAR(32) DEFAULT NULL,
  W_STREET_2 VARCHAR(32) DEFAULT NULL,
  W_CITY VARCHAR(32) DEFAULT NULL,
  W_STATE VARCHAR(2) DEFAULT NULL,
  W_ZIP VARCHAR(9) DEFAULT NULL,
  W_TAX FLOAT DEFAULT NULL,
  W_YTD FLOAT DEFAULT NULL,
  CONSTRAINT W_PK_TREE PRIMARY KEY (W_ID)
);
partition table WAREHOUSE on column W_ID;

CREATE TABLE DISTRICT (
  D_ID TINYINT DEFAULT '0' NOT NULL,
  D_W_ID SMALLINT DEFAULT '0' NOT NULL,
  D_NAME VARCHAR(16) DEFAULT NULL,
  D_STREET_1 VARCHAR(32) DEFAULT NULL,
  D_STREET_2 VARCHAR(32) DEFAULT NULL,
  D_CITY VARCHAR(32) DEFAULT NULL,
  D_STATE VARCHAR(2) DEFAULT NULL,
  D_ZIP VARCHAR(9) DEFAULT NULL,
  D_TAX FLOAT DEFAULT NULL,
  D_YTD FLOAT DEFAULT NULL,
  D_NEXT_O_ID INT DEFAULT NULL,
  CONSTRAINT D_PK_HASH PRIMARY KEY (D_W_ID,D_ID)
);
partition table DISTRICT on column D_W_ID;


CREATE TABLE ITEM (
  I_ID INTEGER DEFAULT '0' NOT NULL,
  I_IM_ID INTEGER DEFAULT NULL,
  I_NAME VARCHAR(32) DEFAULT NULL,
  I_PRICE FLOAT DEFAULT NULL,
  I_DATA VARCHAR(64) DEFAULT NULL,
  CONSTRAINT I_PK_TREE PRIMARY KEY (I_ID)
);

CREATE TABLE CUSTOMER (
  C_ID INTEGER DEFAULT '0' NOT NULL,
  C_D_ID TINYINT DEFAULT '0' NOT NULL,
  C_W_ID SMALLINT DEFAULT '0' NOT NULL,
  C_FIRST VARCHAR(32) DEFAULT NULL,
  C_MIDDLE VARCHAR(2) DEFAULT NULL,
  C_LAST VARCHAR(32) DEFAULT NULL,
  C_STREET_1 VARCHAR(32) DEFAULT NULL,
  C_STREET_2 VARCHAR(32) DEFAULT NULL,
  C_CITY VARCHAR(32) DEFAULT NULL,
  C_STATE VARCHAR(2) DEFAULT NULL,
  C_ZIP VARCHAR(9) DEFAULT NULL,
  C_PHONE VARCHAR(32) DEFAULT NULL,
  C_SINCE TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  C_CREDIT VARCHAR(2) DEFAULT NULL,
  C_CREDIT_LIM FLOAT DEFAULT NULL,
  C_DISCOUNT FLOAT DEFAULT NULL,
  C_BALANCE FLOAT DEFAULT NULL,
  C_YTD_PAYMENT FLOAT DEFAULT NULL,
  C_PAYMENT_CNT INTEGER DEFAULT NULL,
  C_DELIVERY_CNT INTEGER DEFAULT NULL,
  C_DATA VARCHAR(500),
  CONSTRAINT C_PK_HASH PRIMARY KEY (C_W_ID,C_D_ID,C_ID),
  CONSTRAINT C_U_TREE UNIQUE (C_W_ID,C_D_ID,C_LAST,C_FIRST)
);
partition table CUSTOMER on column C_W_ID;
CREATE INDEX IDX_CUSTOMER_TREE ON CUSTOMER (C_W_ID,C_D_ID,C_LAST);

CREATE TABLE CUSTOMER_NAME (
  C_ID INTEGER DEFAULT '0' NOT NULL,
  C_D_ID TINYINT DEFAULT '0' NOT NULL,
  C_W_ID SMALLINT DEFAULT '0' NOT NULL,
  C_FIRST VARCHAR(32) DEFAULT NULL,
  C_LAST VARCHAR(32) DEFAULT NULL
);
CREATE INDEX IDX_CUSTOMER_NAME_TREE ON CUSTOMER_NAME (C_W_ID,C_D_ID,C_LAST);

CREATE TABLE HISTORY (
  H_C_ID INTEGER DEFAULT NULL,
  H_C_D_ID TINYINT DEFAULT NULL,
  H_C_W_ID SMALLINT DEFAULT NULL,
  H_D_ID TINYINT DEFAULT NULL,
  H_W_ID SMALLINT DEFAULT '0' NOT NULL,
  H_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  H_AMOUNT FLOAT DEFAULT NULL,
  H_DATA VARCHAR(32) DEFAULT NULL
);
partition table HISTORY on column H_W_ID;

CREATE TABLE STOCK (
  S_I_ID INTEGER DEFAULT '0' NOT NULL,
  S_W_ID SMALLINT DEFAULT '0 ' NOT NULL,
  S_QUANTITY INTEGER DEFAULT '0' NOT NULL,
  S_DIST_01 VARCHAR(32) DEFAULT NULL,
  S_DIST_02 VARCHAR(32) DEFAULT NULL,
  S_DIST_03 VARCHAR(32) DEFAULT NULL,
  S_DIST_04 VARCHAR(32) DEFAULT NULL,
  S_DIST_05 VARCHAR(32) DEFAULT NULL,
  S_DIST_06 VARCHAR(32) DEFAULT NULL,
  S_DIST_07 VARCHAR(32) DEFAULT NULL,
  S_DIST_08 VARCHAR(32) DEFAULT NULL,
  S_DIST_09 VARCHAR(32) DEFAULT NULL,
  S_DIST_10 VARCHAR(32) DEFAULT NULL,
  S_YTD INTEGER DEFAULT NULL,
  S_ORDER_CNT INTEGER DEFAULT NULL,
  S_REMOTE_CNT INTEGER DEFAULT NULL,
  S_DATA VARCHAR(64) DEFAULT NULL,
  CONSTRAINT S_PK_HASH PRIMARY KEY (S_W_ID,S_I_ID)
);
partition table STOCK on column S_W_ID;

CREATE TABLE ORDERS (
  O_ID INTEGER DEFAULT '0' NOT NULL,
  O_D_ID TINYINT DEFAULT '0' NOT NULL,
  O_W_ID SMALLINT DEFAULT '0' NOT NULL,
  O_C_ID INTEGER DEFAULT NULL,
  O_ENTRY_D TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  O_CARRIER_ID INTEGER DEFAULT NULL,
  O_OL_CNT INTEGER DEFAULT NULL,
  O_ALL_LOCAL INTEGER DEFAULT NULL,
  CONSTRAINT O_PK_HASH PRIMARY KEY (O_W_ID,O_D_ID,O_ID),
  CONSTRAINT O_U_HASH UNIQUE (O_W_ID,O_D_ID,O_C_ID,O_ID)
);
partition table ORDERS on column O_W_ID;
CREATE INDEX IDX_ORDERS_HASH ON ORDERS (O_W_ID,O_D_ID,O_C_ID);

CREATE TABLE NEW_ORDER (
  NO_O_ID INTEGER DEFAULT '0' NOT NULL,
  NO_D_ID TINYINT DEFAULT '0' NOT NULL,
  NO_W_ID SMALLINT DEFAULT '0' NOT NULL,
  CONSTRAINT NO_PK_TREE PRIMARY KEY (NO_D_ID,NO_W_ID,NO_O_ID)
);
partition table NEW_ORDER on column NO_W_ID;

CREATE TABLE ORDER_LINE (
  OL_O_ID INTEGER DEFAULT '0' NOT NULL,
  OL_D_ID TINYINT DEFAULT '0' NOT NULL,
  OL_W_ID SMALLINT DEFAULT '0' NOT NULL,
  OL_NUMBER INTEGER DEFAULT '0' NOT NULL,
  OL_I_ID INTEGER DEFAULT NULL,
  OL_SUPPLY_W_ID SMALLINT DEFAULT NULL,
  OL_DELIVERY_D TIMESTAMP DEFAULT NULL,
  OL_QUANTITY INTEGER DEFAULT NULL,
  OL_AMOUNT FLOAT DEFAULT NULL,
  OL_DIST_INFO VARCHAR(32) DEFAULT NULL,
  CONSTRAINT OL_PK_HASH PRIMARY KEY (OL_W_ID,OL_D_ID,OL_O_ID,OL_NUMBER)
);
partition table ORDER_LINE on column OL_W_ID;
--CREATE INDEX IDX_ORDER_LINE_3COL ON ORDER_LINE (OL_W_ID,OL_D_ID,OL_O_ID);
--CREATE INDEX IDX_ORDER_LINE_2COL ON ORDER_LINE (OL_W_ID,OL_D_ID);
CREATE INDEX IDX_ORDER_LINE_TREE ON ORDER_LINE (OL_W_ID,OL_D_ID,OL_O_ID);

CREATE TABLE LOADER_PERMIT(
PERMIT INTEGER DEFAULT NULL,
  PRIMARY KEY (PERMIT));
CREATE TABLE RUN_PERMIT(
PERMIT INTEGER DEFAULT NULL,
  PRIMARY KEY (PERMIT));

END_OF_BATCH

LOAD CLASSES tpcc-procs.jar;

-- The following CREATE PROCEDURE statements can all be batched.
file -inlinebatch END_OF_2ND_BATCH

create procedure from class com.procedures.LoadWarehouse;
create procedure from class com.procedures.LoadWarehouseReplicated;
create procedure from class com.procedures.ostatByCustomerId;
create procedure from class com.procedures.delivery;
create procedure from class com.procedures.paymentByCustomerNameW;
create procedure from class com.procedures.paymentByCustomerIdC;
create procedure from class com.procedures.paymentByCustomerIdW;
create procedure from class com.procedures.neworder;
create procedure from class com.procedures.slev;
create procedure from class com.procedures.ResetWarehouse;
create procedure from class com.procedures.ostatByCustomerName;
create procedure from class com.procedures.paymentByCustomerNameC;

-- Can't declare partitioning here if also declared in annotations
-- create procedure partition on table warehouse columm w_id from class com.procedures.LoadWarehouse;
-- create procedure partition on table warehouse column w_id from class com.procedures.LoadWarehouseReplicated;
-- create procedure partition on table warehouse column w_id from class com.procedures.ostatByCustomerId;
-- create procedure partition on table warehouse column w_id from class com.procedures.delivery;
-- create procedure partition on table warehouse column w_id from class com.procedures.paymentByCustomerNameW;
-- create procedure partition on table warehouse column w_id from class com.procedures.paymentByCustomerIdC;
-- create procedure partition on table warehouse column w_id from class com.procedures.paymentByCustomerIdW;
-- create procedure partition on table warehouse column w_id from class com.procedures.neworder;
-- create procedure partition on table warehouse column w_id from class com.procedures.slev;
-- create procedure partition on table warehouse column w_id from class com.procedures.ResetWarehouse;
-- create procedure partition on table customer column c_w_id parameter 3 from class com.procedures.ostatByCustomerName;
-- create procedure partition on table customer column c_w_id parameter 3 from class com.procedures.paymentByCustomerNameC;

-- Multi-partition procedures
create procedure from class com.procedures.paymentByCustomerName;
create procedure from class com.procedures.paymentByCustomerId;
create procedure from class com.procedures.LoadStatus;

END_OF_2ND_BATCH