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
  aiRecommended?: boolean;
  inStock?: boolean;
  brand?: string;
  discount?: number;
  subcategory?: string;
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

export type TemplateType =
  | 'THREE_CARDS'
  | 'FOUR_CARDS'
  | 'FIVE_CARDS'
  | 'BUBBLE_LEFT'
  | 'BUBBLE_BOTTOM'
  | 'FUNCTIONAL_GRID';

export type TitleType = 'TEXT' | 'IMAGE';

export interface SceneFunction {
  id: string;
  projectId: string;
  name: string;
  icon: string;
  activationCommand: string;
  orderIndex: number;
  color: string;
  description: string;
}

export interface SceneProject {
  id: string;
  name: string;
  titleType: TitleType;
  titleText: string;
  titleImageUrl: string;
  templateType: TemplateType;
  backgroundImageUrl: string;
  isActive: boolean;
  functions: SceneFunction[];
}

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

export const TEMPLATE_LABELS: Record<TemplateType, string> = {
  THREE_CARDS: 'Plantilla de tres tarjetas',
  FOUR_CARDS: 'Plantillas de cuatro tarjetas',
  FIVE_CARDS: 'Plantilla de cinco tarjetas',
  BUBBLE_LEFT: 'Plantilla de burbuja del lado izquierdo',
  BUBBLE_BOTTOM: 'Plantilla de burbuja inferior',
  FUNCTIONAL_GRID: 'Plantilla de experiencia funcional',
};
