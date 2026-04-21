CREATE USER dipdv_app WITH PASSWORD 'dipdv_test';
GRANT ALL PRIVILEGES ON DATABASE dipdv_test TO dipdv_app;
ALTER DATABASE dipdv_test OWNER TO dipdv_app;
