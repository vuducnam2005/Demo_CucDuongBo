import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { VroadBrand } from '../components/brand/VroadBrand';

describe('Nhận diện VROAD demo', () => {
  it('hiển thị thương hiệu và công bố đây là wordmark tạm', () => {
    render(<VroadBrand inverse />);
    expect(screen.getByLabelText(/VROAD — Quản lý hạ tầng đường bộ/)).toHaveClass('vroad-brand-inverse');
    expect(screen.getByTitle(/Wordmark VROAD tạm thời/)).toBeInTheDocument();
    expect(screen.getByText(/HẠ TẦNG ĐƯỜNG BỘ · DEMO/)).toBeInTheDocument();
  });

  it('giữ tên truy cập được khi sidebar thu gọn', () => {
    render(<VroadBrand compact />);
    expect(screen.getByLabelText(/VROAD — Quản lý hạ tầng đường bộ/)).toBeInTheDocument();
    expect(screen.queryByText(/HẠ TẦNG ĐƯỜNG BỘ · DEMO/)).not.toBeInTheDocument();
  });
});
