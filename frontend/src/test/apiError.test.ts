import { describe, expect, it } from 'vitest';
import { getApiErrorMessage, getApiErrorPresentation, shouldRetryQuery } from '../services/api';

describe('getApiErrorMessage', () => {
  it('does not expose backend SQL, driver, or secret details', () => {
    const error = {
      response: {
        status: 500,
        data: { message: 'database relation raw_dataset_record failed: password=secret' },
      },
    };

    expect(getApiErrorMessage(error)).toBe('Không thể kết nối tới máy chủ. Vui lòng thử lại.');
  });

  it('keeps safe user-facing validation messages', () => {
    const error = {
      response: {
        status: 400,
        data: { message: 'Dữ liệu yêu cầu không hợp lệ', errors: ['email: Email không hợp lệ'] },
      },
    };

    expect(getApiErrorMessage(error)).toBe(
      'Dữ liệu yêu cầu không hợp lệ: email: Email không hợp lệ'
    );
  });
});

describe('getApiErrorPresentation', () => {
  it.each([
    [401, 'Phiên đăng nhập đã hết hạn', false],
    [403, 'Không có quyền truy cập', false],
    [404, 'Không tìm thấy dữ liệu', false],
    [409, 'Dữ liệu đã thay đổi', false],
    [422, 'Dữ liệu chưa đáp ứng quy tắc nghiệp vụ', false],
    [500, 'Hệ thống đang gặp sự cố', true],
  ])('phân biệt HTTP %s', (status, title, retryable) => {
    const presentation = getApiErrorPresentation({
      response: { status, data: { message: `Lỗi ${status}` } },
    });

    expect(presentation).toMatchObject({ status, title, retryable });
  });

  it('chỉ retry một lần cho lỗi mạng hoặc lỗi máy chủ', () => {
    expect(shouldRetryQuery(0, { response: { status: 500 } })).toBe(true);
    expect(shouldRetryQuery(1, { response: { status: 500 } })).toBe(false);
    expect(shouldRetryQuery(0, { response: { status: 409 } })).toBe(false);
    expect(shouldRetryQuery(0, new Error('network unavailable'))).toBe(true);
  });
});
