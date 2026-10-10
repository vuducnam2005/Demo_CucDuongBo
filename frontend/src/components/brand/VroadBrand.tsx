import "./VroadBrand.css";

export interface VroadBrandProps {
  compact?: boolean;
  inverse?: boolean;
}

export const VroadBrand = ({
  compact = false,
  inverse = false,
}: VroadBrandProps) => (
  <div
    className={`vroad-brand${inverse ? " vroad-brand-inverse" : ""}${compact ? " vroad-brand-compact" : ""}`}
  >
    <img
      className="vroad-brand-image"
      src="/vroad-ai-banner.jpg"
      alt="Vroad ai"
      title="Vroad ai"
    />
  </div>
);
