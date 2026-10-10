import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { VroadBrand } from "../components/brand/VroadBrand";

describe("Nhận diện thương hiệu Vroad", () => {
  it("hiển thị logo Vroad tinh tế, không dùng ảnh cũ vroad-ai-banner", () => {
    const { container } = render(<VroadBrand inverse />);
    expect(screen.getByRole("banner", { name: /Vroad/i })).toBeInTheDocument();
    expect(screen.getByText("V")).toBeInTheDocument();
    expect(screen.getByText("road")).toBeInTheDocument();
    expect(container.querySelector("img")).toBeNull();
    expect(screen.queryByText(/DEMO/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/^ai$/i)).not.toBeInTheDocument();
  });

  it("hỗ trợ hiển thị thu gọn khi sidebar compact", () => {
    const { container } = render(<VroadBrand compact />);
    const brandElement = screen.getByRole("banner", { name: /Vroad/i });
    expect(brandElement).toHaveClass("vroad-brand-compact");
    expect(container.querySelector(".vroad-brand-text")).toBeNull();
    expect(container.querySelector(".vroad-brand-icon")).toBeInTheDocument();
  });
});
