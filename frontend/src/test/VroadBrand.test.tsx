import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { VroadBrand } from "../components/brand/VroadBrand";

describe("Nhận diện Vroad ai", () => {
  it("hiển thị ảnh thương hiệu mới không có chữ demo", () => {
    render(<VroadBrand inverse />);
    expect(screen.getByRole("img", { name: "Vroad ai" })).toHaveAttribute(
      "src",
      "/vroad-ai-banner.jpg",
    );
    expect(screen.queryByText(/DEMO/i)).not.toBeInTheDocument();
  });

  it("giữ ảnh thương hiệu khi sidebar thu gọn", () => {
    render(<VroadBrand compact />);
    expect(screen.getByRole("img", { name: "Vroad ai" })).toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: "Vroad ai" }).parentElement,
    ).toHaveClass("vroad-brand-compact");
  });
});
