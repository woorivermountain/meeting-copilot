import Stripe from 'stripe'
import { z } from 'zod'
import { parseRequestBody } from '../../utils/http'
import { appRepository } from '../../repositories/app'
import { requireSession } from '../../utils/session'

const checkoutSchema = z.object({
  teamId: z.uuid(),
  seats: z.number().int().min(3).max(100)
})

export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig(event)
  if (!config.stripeSecretKey || !config.stripeTeamPriceId) {
    throw createError({ statusCode: 503, statusMessage: 'BILLING_NOT_CONFIGURED' })
  }
  const authSession = requireSession(event)
  const input = await parseRequestBody(event, checkoutSchema)
  await appRepository().requireTeamRole(input.teamId, authSession.sub, true)
  const customer = await appRepository().userById(authSession.sub)
  if (!customer) throw createError({ statusCode: 401, statusMessage: 'AUTH_REQUIRED' })
  const stripe = new Stripe(config.stripeSecretKey)
  const checkoutSession = await stripe.checkout.sessions.create({
    mode: 'subscription',
    customer_email: customer.email,
    line_items: [{ price: config.stripeTeamPriceId, quantity: input.seats }],
    allow_promotion_codes: true,
    billing_address_collection: 'auto',
    tax_id_collection: { enabled: true },
    metadata: { teamId: input.teamId },
    subscription_data: { metadata: { teamId: input.teamId } },
    success_url: `${config.public.appUrl}/?billing=success`,
    cancel_url: `${config.public.appUrl}/?billing=cancelled`
  })
  return { url: checkoutSession.url }
})
