CREATE TABLE appuser (
  id   BIGSERIAL PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  isadmin BOOLEAN NOT NULL
);

CREATE TABLE apikey (
  apikey VARCHAR(100) PRIMARY KEY,
  userid BIGSERIAL NOT NULL REFERENCES appuser
);

CREATE TABLE claim (
  prime BIGINT PRIMARY KEY,
  owner BIGSERIAL NOT NULL REFERENCES appuser
);

INSERT INTO appuser (id, username, isadmin) VALUES (0, 'admin', true);
