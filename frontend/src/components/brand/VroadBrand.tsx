import React from "react";
import "./VroadBrand.css";

export interface VroadBrandProps {
  compact?: boolean;
  inverse?: boolean;
  size?: "small" | "medium" | "large";
  showSubtitle?: boolean;
  className?: string;
}

export const VroadBrand: React.FC<VroadBrandProps> = ({
  compact = false,
  inverse = false,
  size = "medium",
  showSubtitle = true,
  className = "",
}) => {
  const iconSize = size === "large" ? 42 : size === "small" ? 28 : 34;

  return (
    <div
      className={`vroad-brand vroad-brand-${size}${
        inverse ? " vroad-brand-inverse" : ""
      }${compact ? " vroad-brand-compact" : ""}${
        className ? ` ${className}` : ""
      }`}
      aria-label="Vroad - Quản lý hạ tầng đường bộ"
      role="banner"
    >
      <div className="vroad-brand-icon-wrapper">
        <svg
          width={iconSize}
          height={iconSize}
          viewBox="0 0 36 36"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          className="vroad-brand-icon"
          aria-hidden="true"
        >
          <defs>
            <linearGradient
              id="vroad-brand-bg-grad"
              x1="0"
              y1="0"
              x2="36"
              y2="36"
              gradientUnits="userSpaceOnUse"
            >
              <stop offset="0%" stopColor="#1677ff" />
              <stop offset="55%" stopColor="#0958d9" />
              <stop offset="100%" stopColor="#002766" />
            </linearGradient>
            <linearGradient
              id="vroad-brand-border-grad"
              x1="0"
              y1="0"
              x2="36"
              y2="36"
              gradientUnits="userSpaceOnUse"
            >
              <stop offset="0%" stopColor="rgba(255, 255, 255, 0.45)" />
              <stop offset="100%" stopColor="rgba(255, 255, 255, 0.1)" />
            </linearGradient>
            <linearGradient
              id="vroad-brand-cyan-grad"
              x1="18"
              y1="25"
              x2="27.5"
              y2="9.5"
              gradientUnits="userSpaceOnUse"
            >
              <stop offset="0%" stopColor="#60a5fa" />
              <stop offset="100%" stopColor="#38bdf8" />
            </linearGradient>
          </defs>

          {/* Nền badge bo góc hiện đại */}
          <rect
            width="36"
            height="36"
            rx="9"
            fill="url(#vroad-brand-bg-grad)"
          />
          <rect
            x="0.5"
            y="0.5"
            width="35"
            height="35"
            rx="8.5"
            stroke="url(#vroad-brand-border-grad)"
            strokeWidth="1"
          />

          {/* Dải đường hình chữ V cách điệu */}
          <path
            d="M8.5 9.5L17.2 26.5C17.5 27.1 18.5 27.1 18.8 26.5L27.5 9.5"
            stroke="#FFFFFF"
            strokeWidth="3.2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="M18 25L27.5 9.5"
            stroke="url(#vroad-brand-cyan-grad)"
            strokeWidth="3.2"
            strokeLinecap="round"
          />

          {/* Vạch sơn tim đường đứt nét phản quang đặc trưng đường bộ */}
          <line
            x1="18"
            y1="11"
            x2="18"
            y2="14"
            stroke="#fbbf24"
            strokeWidth="1.8"
            strokeLinecap="round"
          />
          <line
            x1="18"
            y1="17.5"
            x2="18"
            y2="20.5"
            stroke="#fbbf24"
            strokeWidth="1.8"
            strokeLinecap="round"
          />
        </svg>
      </div>

      {!compact && (
        <div className="vroad-brand-text">
          <div className="vroad-brand-title">
            <span className="vroad-brand-v">V</span>
            <span className="vroad-brand-road">road</span>
          </div>
          {showSubtitle && (
            <div className="vroad-brand-sub">HẠ TẦNG ĐƯỜNG BỘ</div>
          )}
        </div>
      )}
    </div>
  );
};
