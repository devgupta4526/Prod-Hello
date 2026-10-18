import type { Environment } from '../../config/env.js';
import { AppError } from '../../lib/app-error.js';
import { z } from 'zod';

export interface SmsInput { toE164: string; code: string; appHash?: string; requestId: string }
export interface SmsProvider { readonly name: string; send(input: SmsInput): Promise<string> }

class HttpSmsProvider implements SmsProvider {
  constructor(readonly name: 'msg91' | 'twilio', private readonly env: Environment) {}
  async send(input: SmsInput): Promise<string> {
    // A network retry can duplicate delivery when the provider accepted the first request.
    // Use the same code/challenge; never issue a second challenge during failover.
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const response = this.name === 'twilio'
          ? await fetch(`https://api.twilio.com/2010-04-01/Accounts/${this.env.TWILIO_ACCOUNT_SID ?? ''}/Messages.json`, {
              method: 'POST', signal: AbortSignal.timeout(5000),
              headers: { Authorization: `Basic ${Buffer.from(`${this.env.TWILIO_ACCOUNT_SID ?? ''}:${this.env.TWILIO_AUTH_TOKEN ?? ''}`).toString('base64')}`, 'Content-Type': 'application/x-www-form-urlencoded' },
              body: new URLSearchParams({ To: input.toE164, From: this.env.TWILIO_FROM ?? '', Body: `<#> Your Hello code is ${input.code}. It expires in 5 minutes. Never share it. ${this.env.SMS_APP_HASH ?? input.appHash ?? ''}`, ...(this.env.TWILIO_STATUS_CALLBACK_URL ? { StatusCallback: this.env.TWILIO_STATUS_CALLBACK_URL } : {}) }),
            })
          : await fetch('https://control.msg91.com/api/v5/flow/', {
              method: 'POST', signal: AbortSignal.timeout(5000),
              headers: { authkey: this.env.MSG91_AUTH_KEY ?? '', 'Content-Type': 'application/json' },
              body: JSON.stringify({ template_id: this.env.MSG91_TEMPLATE_ID, short_url: '0', recipients: [{ mobiles: input.toE164.slice(1), OTP: input.code, APP_HASH: this.env.SMS_APP_HASH ?? input.appHash ?? '' }] }),
            });
        if (!response.ok) throw new AppError('sms_unavailable', 503);
        const payload: unknown = await response.json();
        if (this.name === 'twilio') return z.object({ sid: z.string() }).parse(payload).sid;
        const result = z.object({ type: z.string(), message: z.string() }).parse(payload);
        if (result.type !== 'success') throw new AppError('sms_unavailable', 503);
        return result.message;
      } catch (error) {
        if (error instanceof AppError || attempt === 1) throw new AppError('sms_unavailable', 503);
      }
    }
    throw new AppError('sms_unavailable', 503);
  }
}

class ConsoleProvider implements SmsProvider {
  readonly name = 'console';
  constructor(private readonly env: Environment) {}
  send(input: SmsInput): Promise<string> {
    if (this.env.APP_ENV === 'production') throw new AppError('sms_unavailable', 503);
    // Deliberate local/staging developer console channel; never pino/request logging.
    process.stderr.write(`Development OTP (${input.requestId}): ${input.code}\n`);
    return Promise.resolve(input.requestId);
  }
}

export class FailoverSender {
  constructor(private readonly env: Environment) {}
  async send(input: SmsInput): Promise<{ provider: string; providerMessageId: string }> {
    const names = [...new Set([this.env.OTP_PROVIDER, this.env.OTP_FALLBACK_PROVIDER].filter((name) => name !== undefined))];
    for (const name of names) {
      const provider = name === 'console' ? new ConsoleProvider(this.env) : new HttpSmsProvider(name, this.env);
      try { return { provider: name, providerMessageId: await provider.send(input) }; } catch { /* Try the configured fallback, then fail closed. */ }
    }
    throw new AppError('sms_unavailable', 503);
  }
}
