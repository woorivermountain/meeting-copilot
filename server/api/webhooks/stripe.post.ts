import Stripe from 'stripe'
import { syncSubscription } from '../../repositories/billing'
import { safeOperationalLog } from '../../utils/redaction'

export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig(event)
  if (!config.stripeSecretKey || !config.stripeWebhookSecret) {
    throw createError({ statusCode: 503, statusMessage: 'BILLING_NOT_CONFIGURED' })
  }
  const signature = getHeader(event, 'stripe-signature')
  if (!signature) throw createError({ statusCode: 400, statusMessage: 'SIGNATURE_REQUIRED' })

  const rawBody = await readRawBody(event, false)
  if (!rawBody) throw createError({ statusCode: 400, statusMessage: 'BODY_REQUIRED' })
  const stripe = new Stripe(config.stripeSecretKey)
  let stripeEvent: Stripe.Event
  try {
    stripeEvent = stripe.webhooks.constructEvent(rawBody, signature, config.stripeWebhookSecret)
  } catch {
    throw createError({ statusCode: 400, statusMessage: 'INVALID_SIGNATURE' })
  }

  if ([
    'customer.subscription.created',
    'customer.subscription.updated',
    'customer.subscription.deleted'
  ].includes(stripeEvent.type)) {
    const subscription = stripeEvent.data.object as Stripe.Subscription
    if (config.databaseUrl) await syncSubscription(config.databaseUrl, subscription)
    safeOperationalLog('subscription_changed', {
      stripeEventId: stripeEvent.id,
      subscriptionId: subscription.id,
      status: subscription.status,
      teamId: subscription.metadata.teamId
    })
    // Production adapter: upsert billing_subscriptions by subscription.id (idempotent).
  }
  return { received: true }
})
