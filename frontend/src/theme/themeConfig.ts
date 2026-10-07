import { ThemeConfig } from 'antd';

export const appTheme: ThemeConfig = {
  token: {
    // Government portal brand colors (Cục Đường bộ Việt Nam - Deep Navy Blue)
    colorPrimary: '#003a8c',
    colorInfo: '#1677ff',
    colorSuccess: '#52c41a',
    colorWarning: '#fa8c16',
    colorError: '#ff4d4f',
    colorTextBase: '#262626',
    colorBgBase: '#ffffff',
    colorBgLayout: '#f0f2f5',
    
    // Geometry & Typography
    borderRadius: 6,
    borderRadiusSM: 4,
    borderRadiusLG: 8,
    fontFamily: [
      '-apple-system',
      'BlinkMacSystemFont',
      '"Segoe UI"',
      'Roboto',
      '"Helvetica Neue"',
      'Arial',
      '"Noto Sans"',
      'sans-serif',
      '"Apple Color Emoji"',
      '"Segoe UI Emoji"',
      '"Segoe UI Symbol"',
    ].join(','),
    fontSize: 14,
    fontSizeHeading1: 28,
    fontSizeHeading2: 24,
    fontSizeHeading3: 20,
    fontSizeHeading4: 16,
    fontSizeHeading5: 14,

    // Layout
    wireframe: false,
  },
  components: {
    Layout: {
      headerBg: '#ffffff',
      headerHeight: 56,
      headerPadding: '0 24px',
      siderBg: '#001529',
      footerPadding: '16px 24px',
    },
    Menu: {
      darkItemBg: '#001529',
      darkItemSelectedBg: '#003a8c',
      darkItemHoverBg: '#0c2135',
      darkSubMenuItemBg: '#000c17',
      fontSize: 14,
    },
    Button: {
      controlHeight: 36,
      borderRadius: 6,
      fontWeight: 500,
    },
    Table: {
      headerBg: '#fafafa',
      headerColor: '#1f1f1f',
      headerSplitColor: '#f0f0f0',
      rowHoverBg: '#f5f9ff',
      borderColor: '#f0f0f0',
    },
    Card: {
      headerHeight: 48,
      headerFontSize: 16,
    },
  },
};
