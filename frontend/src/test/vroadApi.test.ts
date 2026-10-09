import { describe, expect, it } from 'vitest';
import { trustedSourceImage } from '../services/vroadApi';

describe('trustedSourceImage', () => {
  it('accepts only an HTTPS link on the precise provider host', () => {
    expect(trustedSourceImage('https://platform.vroad.vn/s/example')).toBe('https://platform.vroad.vn/s/example');
    for (const url of [null, 'javascript:alert(1)', 'http://platform.vroad.vn/s/example',
      'https://platform.vroad.vn.attacker.example/s/test', 'https://platform.vroad.vn:8443/s/test',
      'https://user:pass@platform.vroad.vn/s/test']) {
      expect(trustedSourceImage(url)).toBeNull();
    }
  });
});
