-- Cria o role de aplicação como NÃO-superusuário para que o RLS seja aplicado.
-- Executado antes do Flyway (via withInitScript) no container de testes.
-- O Flyway V2 pula o CREATE ROLE (IF NOT EXISTS) e continua com GRANTs e políticas.
CREATE ROLE dipdv_app WITH LOGIN PASSWORD 'dipdv_test' NOSUPERUSER NOCREATEDB NOCREATEROLE;
