import './VroadBrand.css';

export interface VroadBrandProps {
  compact?: boolean;
  inverse?: boolean;
}

export const VroadBrand = ({ compact = false, inverse = false }: VroadBrandProps) => (
  <div className={`vroad-brand${inverse ? ' vroad-brand-inverse' : ''}`}
    role="img"
    aria-label="VROAD — Quản lý hạ tầng đường bộ, bản demo độc lập"
    title="Wordmark VROAD tạm thời; chưa phải file logo thương hiệu chính thức">
    <span className="vroad-brand-mark" aria-hidden="true"><span /></span>
    {!compact && <span className="vroad-brand-copy">
      <span className="vroad-brand-name">VROAD<span className="vroad-brand-dot">.</span></span>
      <span className="vroad-brand-caption">HẠ TẦNG ĐƯỜNG BỘ · DEMO</span>
    </span>}
  </div>
);
