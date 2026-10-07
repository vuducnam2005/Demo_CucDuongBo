export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export type TypeGuard<T> = (value: unknown) => value is T;

export class ApiContractError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ApiContractError';
  }
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const isNonNegativeInteger = (value: unknown): value is number =>
  typeof value === 'number' && Number.isInteger(value) && value >= 0;

export const normalizePagedResponse = <T>(
  value: unknown,
  isItem: TypeGuard<T>,
  source = 'API'
): PagedResponse<T> => {
  if (!isRecord(value)) {
    throw new ApiContractError(`${source} phải trả về một đối tượng phân trang.`);
  }

  const { content, page, size, totalElements, totalPages, first, last } = value;

  if (!Array.isArray(content) || !content.every(isItem)) {
    throw new ApiContractError(`${source}.content không đúng kiểu danh sách bản ghi.`);
  }
  if (!isNonNegativeInteger(page)) {
    throw new ApiContractError(`${source}.page phải là số nguyên không âm.`);
  }
  if (!isNonNegativeInteger(size) || size === 0) {
    throw new ApiContractError(`${source}.size phải là số nguyên dương.`);
  }
  if (!isNonNegativeInteger(totalElements) || !isNonNegativeInteger(totalPages)) {
    throw new ApiContractError(`${source} có tổng số bản ghi hoặc tổng số trang không hợp lệ.`);
  }
  if (typeof first !== 'boolean' || typeof last !== 'boolean') {
    throw new ApiContractError(`${source} thiếu cờ first/last kiểu boolean.`);
  }

  return { content, page, size, totalElements, totalPages, first, last };
};
