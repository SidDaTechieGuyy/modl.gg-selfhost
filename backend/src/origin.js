export function parseAllowedOrigins(rawValue = '*') {
  if (!rawValue || rawValue.trim() === '') {
    return ['*'];
  }

  if (rawValue.trim() === '*') {
    return ['*'];
  }

  return rawValue
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => entry.replace(/\/+$/, ''));
}

export function isOriginAllowed(origin, rawValue = '*') {
  if (!origin) {
    return true;
  }

  const allowed = parseAllowedOrigins(rawValue);

  if (allowed.includes('*')) {
    return true;
  }

  const normalizedOrigin = origin.replace(/\/+$/, '');
  return allowed.some((candidate) => {
    if (candidate === '*') return true;
    if (candidate.startsWith('https://') || candidate.startsWith('http://')) {
      return normalizedOrigin === candidate;
    }
    return normalizedOrigin.endsWith(candidate);
  });
}

export function buildCorsHeaders(origin, rawValue = '*') {
  const allowed = parseAllowedOrigins(rawValue);

  if (!origin || !allowed.includes('*') && !isOriginAllowed(origin, rawValue)) {
    return {
      allowOrigin: false,
    };
  }

  if (allowed.includes('*')) {
    return {
      allowOrigin: origin ?? '*',
      credentials: true,
    };
  }

  return {
    allowOrigin: origin,
    credentials: true,
  };
}
