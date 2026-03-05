export const createCorrelationId = () =>
  `cid-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;

export const withCorrelationHeader = (headers = {}) => ({
  ...headers,
  'X-Correlation-ID': headers['X-Correlation-ID'] || createCorrelationId(),
});
