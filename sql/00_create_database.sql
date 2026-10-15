-- SQL Server 2019+; chạy với quyền tạo database. Không ghi mật khẩu vào script.
IF DB_ID(N'uteb') IS NULL
    CREATE DATABASE uteb COLLATE Latin1_General_100_CI_AS_SC_UTF8;
GO
USE uteb;
GO
