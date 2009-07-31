set define on

accept dbhost prompt "Enter DB Name ->"
accept syspw prompt "Enter Sys password ->"

connect sys/&&syspw@&&dbhost as sysdba

set define off

create user dftest identified by dftest;
grant connect to dftest;
grant create session to dftest;
grant create procedure to dftest;
grant create table to dftest;
grant resource to dftest;
grant create procedure to dftest;

set define on
connect dftest/dftest@&&dbhost
set define off

create or replace procedure ConcatenateStrings(firstString varchar2, secondString varchar2, concatenated out varchar2) as
begin
	concatenated:=firstString || ' ' || secondString;
end;
/

create or replace function ConcatenateF(firstString varchar2, secondString varchar2) return varchar as
begin
	return firstString || ' ' || secondString;
end;
/

create procedure CalcLength(name varchar2, strlength out number) as
begin
	strlength:=length(name);
end;
/

create sequence s1 start with 1;


create table users(name varchar2(50), username varchar2(50), userid number primary key);

 
CREATE OR REPLACE TRIGGER USERS_BIE
BEFORE INSERT ON USERS
FOR EACH ROW
BEGIN
	  SELECT s1.NEXTVAL INTO :new.userid FROM dual;
END; 
/

create or replace package RCTest as
type URefCursor IS REF CURSOR RETURN USERS%ROWTYPE;
procedure TestRefCursor (howmuch number,lvlcursor out URefCursor);
end; 
/

create or replace package body RCTest as 
procedure TestRefCursor (
howmuch number,
lvlcursor out URefCursor
)
as 
begin
 for i in 1..howmuch loop
 	insert into users(name, username) values ('User '||i, 'Username'||i);	
 end loop;
 OPEN lvlcursor FOR
	  SELECT * FROM users;
 end;
end;	 
/

create or replace function Multiply(n1 number, n2 number) return number as
begin
	return n1*n2;
end;
/


set define on

connect sys/&&syspw@&&dbhost as sysdba

set define off


create user dfsyntest identified by dfsyntest;

create or replace procedure dfsyntest.standaloneproc(num1 number, num2 out number) as
begin
num2:=2*num1;
end;
/

create or replace public synonym synstandaloneproc for dfsyntest.standaloneproc;

create or replace package dfsyntest.pkg as
	procedure pkgproc(num1 number, num2 out number);
end;
/

create or replace package body dfsyntest.pkg as
	procedure pkgproc(num1 number, num2 out number) as
	begin
		num2:=2*num1;
	end;
end;
/
	 
create or replace public synonym synpkg for dfsyntest.pkg;

grant execute on synstandaloneproc to dftest;

grant execute on dfsyntest.pkg to dftest;

exit