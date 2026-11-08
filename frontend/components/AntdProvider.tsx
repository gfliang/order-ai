"use client";

import type { ReactNode } from "react";
import { App, ConfigProvider } from "antd";
import zhCN from "antd/locale/zh_CN";

export default function AntdProvider({ children }: { children: ReactNode }) {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{ token: { colorPrimary: "#1677ff", borderRadius: 8 } }}
    >
      <App>{children}</App>
    </ConfigProvider>
  );
}
