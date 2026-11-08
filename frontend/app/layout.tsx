import type { Metadata } from "next";
import type { ReactNode } from "react";
import { AntdRegistry } from "@ant-design/nextjs-registry";
import "@ant-design/v5-patch-for-react-19";
import AppHeader from "@/components/AppHeader";
import AntdProvider from "@/components/AntdProvider";
import "./globals.css";

export const metadata: Metadata = {
  title: "AI 智能订单管理系统",
  description: "基于 Qwen-Max 的智能订单管理演示系统",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>
        <AntdRegistry>
          <AntdProvider>
            <AppHeader />
            {children}
          </AntdProvider>
        </AntdRegistry>
      </body>
    </html>
  );
}
