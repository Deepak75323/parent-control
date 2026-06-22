-- Billing provider metadata (Stripe / Razorpay)

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS provider VARCHAR(32),
    ADD COLUMN IF NOT EXISTS provider_customer_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_subscription_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_subscriptions_parent ON subscriptions (parent_profile_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions (status);
