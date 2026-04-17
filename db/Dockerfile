FROM mysql:8.0

COPY init.sql /docker-entrypoint-initdb.d

ENV MYSQL_ROOT_PASSWORD=root1234
ENV MYSQL_DATABASE=metadb

CMD ["--port=3307", "--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci"]
