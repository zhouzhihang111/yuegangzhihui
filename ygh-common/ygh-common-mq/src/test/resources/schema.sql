DROP TABLE IF EXISTS mq_dead_letter;
DROP TABLE IF EXISTS mq_business_effect;
DROP TABLE IF EXISTS mq_consumption;

CREATE TABLE mq_consumption (
    consumer_group VARCHAR(64) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    owner VARCHAR(128),
    lease_until TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (consumer_group, event_id)
);

CREATE TABLE mq_dead_letter (
    consumer_group VARCHAR(64) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version INTEGER NOT NULL,
    business_key VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    delivery_attempt INTEGER NOT NULL,
    failure_code VARCHAR(64) NOT NULL,
    failed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (consumer_group, event_id)
);

CREATE TABLE mq_business_effect (
    event_id VARCHAR(128) PRIMARY KEY,
    effect_value VARCHAR(64) NOT NULL
);
