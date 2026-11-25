-- Execute como administrador e troque a senha abaixo antes de usar.
CREATE DATABASE IF NOT EXISTS falcon CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'falcon'@'localhost' IDENTIFIED BY 'ALTERE_ESTA_SENHA';
GRANT SELECT, INSERT, UPDATE, CREATE, ALTER, INDEX, REFERENCES ON falcon.* TO 'falcon'@'localhost';
-- Configure a mesma senha em config.properties. A aplicação cria as tabelas.
