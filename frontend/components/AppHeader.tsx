"use client";

import { Menu } from "antd";
import { usePathname, useRouter } from "next/navigation";

const NAV_ITEMS = [
  { key: "/", label: "AI 智能助手" },
  { key: "/orders", label: "订单管理" },
];

export default function AppHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const selectedKey = pathname?.startsWith("/orders") ? "/orders" : "/";

  return (
    <header className="app-header">
      <div className="app-brand">AI 智能订单管理系统</div>
      <Menu
        className="app-nav"
        mode="horizontal"
        items={NAV_ITEMS}
        selectedKeys={[selectedKey]}
        onClick={({ key }) => router.push(key)}
      />
    </header>
  );
}
