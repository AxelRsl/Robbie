export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  currency: string;
  imageUrl: string;
  category: string;
  stock: number;
  tags: string[];
}

export interface SearchResult {
  products: Product[];
  query: string;
  totalResults: number;
}

export interface PromoVideo {
  id: string;
  title: string;
  videoUrl: string;
  thumbnailUrl: string;
  duration: number;
  description: string;
}

export interface Promotion {
  id: string;
  title: string;
  description: string;
  imageUrl?: string;
  videoUrl?: string;
  products: Product[];
  startDate: string;
  endDate: string;
  discount: number;
}

export interface UiConfig {
  showSearchBar: boolean;
  showMicButton: boolean;
  retailColumns: number;
}

export type RetailTemplate = 'grid' | 'list';
export type MenuTemplate = 'classic' | 'modern';
export type PromoTemplate = 'video' | 'carousel';

export interface AppMode {
  id: string;
  name: string;
  icon: string;
  enabled: boolean;
}

export interface CloudConfig {
  apiDomain: string;
  biDomain: string;
  aiOpenDomain: string;
  appId: string;
  appSecret: string;
  clientId: string;
  region: string;
}

export interface RobotConfig {
  cloudConfig: CloudConfig;
  retailTemplate: RetailTemplate;
  menuTemplate: MenuTemplate;
  promoTemplate: PromoTemplate;
  enabledModes: string[];
}

export interface OPKPlugin {
  filename: string;
  name: string;
  version: string;
}

export interface NavigationState {
  destination: string;
  isNavigating: boolean;
  estimatedTime?: number;
  distance?: number;
}
