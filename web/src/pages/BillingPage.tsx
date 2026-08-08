import {useCallback, useEffect, useState} from 'react';
import {billingApi} from '../api/services';
import type {PlanInfo, SubscriptionInfo} from '../types/api';

export function BillingPage() {
  const [sub, setSub] = useState<SubscriptionInfo | null>(null);
  const [plans, setPlans] = useState<PlanInfo[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [subRes, plansRes] = await Promise.all([
        billingApi.subscription(),
        billingApi.plans(),
      ]);
      setSub(subRes.data.data ?? null);
      setPlans(plansRes.data.data ?? []);
    } catch {
      setError('Failed to load billing info');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const onCheckout = async (planId: string) => {
    setBusy(planId);
    setError(null);
    try {
      const {data} = await billingApi.checkout(planId);
      const url = data.data?.checkoutUrl;
      if (!url) {
        setError('Checkout URL missing');
        return;
      }
      window.location.assign(url);
    } catch {
      setError('Could not start checkout');
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Billing</h1>
          <p className="muted">Subscription and plan upgrades (Stripe checkout).</p>
        </div>
        <button type="button" className="btn ghost" onClick={() => void load()}>
          Refresh
        </button>
      </div>
      {error ? <div className="error">{error}</div> : null}

      <div className="card">
        <h2>Current subscription</h2>
        {!sub ? (
          <p className="muted">No subscription data.</p>
        ) : (
          <dl className="meta grid-3">
            <div>
              <dt>Plan</dt>
              <dd>{sub.planId}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>
                {sub.status}
                {sub.active ? ' · active' : ''}
              </dd>
            </div>
            <div>
              <dt>Devices</dt>
              <dd>up to {sub.maxDevices}</dd>
            </div>
            <div>
              <dt>Live streaming</dt>
              <dd>{sub.liveStreamingAllowed ? 'Allowed' : 'Not included'}</dd>
            </div>
            <div>
              <dt>Period end</dt>
              <dd>
                {sub.currentPeriodEnd
                  ? new Date(sub.currentPeriodEnd).toLocaleString()
                  : '—'}
              </dd>
            </div>
            <div>
              <dt>Trial ends</dt>
              <dd>{sub.trialEndsAt ? new Date(sub.trialEndsAt).toLocaleString() : '—'}</dd>
            </div>
          </dl>
        )}
      </div>

      <div className="card">
        <h2>Plans</h2>
        {plans.length === 0 ? (
          <p className="muted">No plans available.</p>
        ) : (
          <div className="device-grid">
            {plans.map(p => (
              <div key={p.planId} className="card">
                <h2>{p.planId}</h2>
                <p className="muted">
                  {p.maxDevices} device{p.maxDevices === 1 ? '' : 's'}
                  {p.liveStreamingAllowed ? ' · live streaming' : ''}
                </p>
                <button
                  type="button"
                  className="btn primary"
                  disabled={busy === p.planId || sub?.planId === p.planId}
                  onClick={() => void onCheckout(p.planId)}>
                  {sub?.planId === p.planId
                    ? 'Current plan'
                    : busy === p.planId
                      ? 'Redirecting…'
                      : 'Upgrade'}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
