CREATE TABLE process (
  id character varying(40) NOT NULL,
  at bigint NOT NULL,
  notifications character varying(2048) NOT NULL,
  CONSTRAINT process_pkey PRIMARY KEY (id)
) WITH (OIDS=FALSE);

CREATE INDEX idx_process_at ON process USING btree (at);

CREATE TABLE property (
  property_key character varying(128) NOT NULL,
  property_value character varying(2048) NOT NULL,
  CONSTRAINT property_pkey PRIMARY KEY (property_key)
) WITH (OIDS=FALSE);
