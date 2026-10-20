CREATE TABLE external_request_budget (
  budget_date DATE NOT NULL,
  provider_kind VARCHAR(20) NOT NULL,
  calls INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (budget_date, provider_kind)
);
