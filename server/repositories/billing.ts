import type Stripe from 'stripe'
import { database } from '../utils/database'

const supportedStatuses = new Set(['trialing', 'active', 'past_due', 'canceled', 'unpaid', 'incomplete'])

export async function syncSubscription(url: string, subscription: Stripe.Subscription) {
  const teamId = subscription.metadata.teamId
  const item = subscription.items.data[0]
  if (!teamId || !item) throw new Error('SUBSCRIPTION_METADATA_INVALID')
  const product = typeof item.price.product === 'string' ? item.price.product : item.price.product.id
  const customer = typeof subscription.customer === 'string' ? subscription.customer : subscription.customer.id
  const status = supportedStatuses.has(subscription.status) ? subscription.status : 'incomplete'
  const sql = database(url)
  await sql`
    insert into billing_subscriptions (
      team_id, stripe_customer_id, stripe_subscription_id, stripe_product_id, status, seats, updated_at
    ) values (
      ${teamId}, ${customer}, ${subscription.id}, ${product}, ${status}, ${item.quantity || 1}, now()
    )
    on conflict (team_id) do update set
      stripe_customer_id = excluded.stripe_customer_id,
      stripe_subscription_id = excluded.stripe_subscription_id,
      stripe_product_id = excluded.stripe_product_id,
      status = excluded.status,
      seats = excluded.seats,
      updated_at = now()
  `
}
