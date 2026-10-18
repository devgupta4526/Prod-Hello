export class AppError extends Error {
  constructor(public readonly code: string, public readonly httpStatus: number, public readonly retryAfter?: number) {
    super(code);
  }
}
