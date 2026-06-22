# Billing

## Plans

| Plan | Devices | Live streaming |
|------|---------|----------------|
| `free` | 1 | No |
| `standard` | 3 | Yes |
| `family` | 10 | Yes |

New parents receive a **14-day trial** (Standard features) on registration.

## API

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/billing/plans` | Parent |
| GET | `/api/v1/billing/subscription` | Parent |
| POST | `/api/v1/billing/checkout` | Parent — body `{ "planId": "standard" }` |
| POST | `/api/v1/billing/webhooks/stripe` | Public (Stripe signature) |
| POST | `/api/v1/billing/webhooks/razorpay` | Public (stub) |

## Feature gates

When `BILLING_ENFORCEMENT=true`:

- Device pairing checks device limit
- Policy create requires active subscription
- Live stream requires Standard/Family (or trial)

Set `BILLING_ENFORCEMENT=false` for local dev without Stripe.

## Stripe setup

```env
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PRICE_STANDARD=price_...
STRIPE_PRICE_FAMILY=price_...
CHECKOUT_SUCCESS_URL=https://your-app/success
CHECKOUT_CANCEL_URL=https://your-app/cancel
```

Webhook URL: `https://api.example.com/api/v1/billing/webhooks/stripe`

Events: `checkout.session.completed`, `invoice.paid`, `customer.subscription.updated`, `customer.subscription.deleted`
