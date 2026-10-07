type SafeErrorContext = Record<string, string | number | boolean | undefined>;

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

const getStatus = (error: unknown): number | undefined => {
  if (!isRecord(error) || !isRecord(error.response)) return undefined;
  return typeof error.response.status === 'number' ? error.response.status : undefined;
};

/** Report client failures without sending exception messages or stack paths in production. */
export const reportClientError = (error: unknown, context: SafeErrorContext): void => {
  if (import.meta.env.DEV) {
    console.error('Client error', error, context);
    return;
  }

  const rawErrorType = error instanceof Error ? error.name : 'UnknownError';
  const errorType = /^[A-Za-z][A-Za-z0-9_$]{0,40}$/.test(rawErrorType)
    ? rawErrorType
    : 'UnknownError';
  console.error('Client error', {
    errorType,
    httpStatus: getStatus(error),
    ...context,
  });
};
