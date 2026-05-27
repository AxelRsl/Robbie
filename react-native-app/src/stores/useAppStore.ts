import { create } from 'zustand';
import type { RetailTemplate, MenuTemplate, PromoTemplate, Product, UiConfig, NavigationState, SceneProject } from '@/types';

const DEFAULT_UI_CONFIG: UiConfig = {
  showSearchBar: false,
  showMicButton: false,
  retailColumns: 4,
};

export interface ChargingUiState {
  status: string;
  message: string;
  batteryLevel: number | null;
  isCharging: boolean;
  isNavigatingToCharger: boolean;
  robotApiConnected: boolean;
  autoTriggered: boolean;
}

export interface AgentUiState {
  status: string;
  message: string;
  gateOpen: boolean;
  personVisible: boolean;
}

const DEFAULT_CHARGING_STATE: ChargingUiState = {
  status: '',
  message: '',
  batteryLevel: null,
  isCharging: false,
  isNavigatingToCharger: false,
  robotApiConnected: false,
  autoTriggered: false,
};

const DEFAULT_AGENT_STATE: AgentUiState = {
  status: 'reset_status',
  message: '',
  gateOpen: false,
  personVisible: false,
};

function isSameChargingState(a: ChargingUiState, b: ChargingUiState) {
  return a.status === b.status
    && a.message === b.message
    && a.batteryLevel === b.batteryLevel
    && a.isCharging === b.isCharging
    && a.isNavigatingToCharger === b.isNavigatingToCharger
    && a.robotApiConnected === b.robotApiConnected
    && a.autoTriggered === b.autoTriggered;
}

interface AppState {
  retailTemplate: RetailTemplate;
  menuTemplate: MenuTemplate;
  promoTemplate: PromoTemplate;
  currentMode: string;
  searchQuery: string;
  selectedProduct: Product | null;
  uiConfig: UiConfig;
  navigation: NavigationState | null;
  searchRecommendation: string;
  searchResults: Product[];
  products: Product[];
  productsLoaded: boolean;
  sceneProject: SceneProject | null;
  sceneProjectLoaded: boolean;
  charging: ChargingUiState;
  agent: AgentUiState;
  chargingStatus: string;
  chargingMessage: string;

  setRetailTemplate: (template: RetailTemplate) => void;
  setMenuTemplate: (template: MenuTemplate) => void;
  setPromoTemplate: (template: PromoTemplate) => void;
  setCurrentMode: (mode: string) => void;
  setSearchQuery: (query: string) => void;
  setSelectedProduct: (product: Product | null) => void;
  setUiConfig: (config: Partial<UiConfig>) => void;
  setNavigation: (navigation: NavigationState | null) => void;
  startNavigation: (destination: string, estimatedTime?: number, distance?: number) => void;
  setSearchRecommendation: (recommendation: string) => void;
  setSearchResults: (results: Product[]) => void;
  applyRetailSearchPayload: (payload: { results: Product[]; query: string; recommendation: string; mode?: string }) => void;
  setProducts: (products: Product[]) => void;
  setProductsLoaded: (loaded: boolean) => void;
  setSceneProject: (project: SceneProject | null) => void;
  setChargingState: (state: Partial<ChargingUiState>) => void;
  resetChargingState: (force?: boolean) => void;
  setChargingStatus: (status: string, message: string) => void;
  setAgentStatus: (status: string, message: string) => void;
  setListeningGate: (gateOpen: boolean, personVisible: boolean) => void;
}

export const useAppStore = create<AppState>((set) => ({
  retailTemplate: 'grid',
  menuTemplate: 'classic',
  promoTemplate: 'video',
  currentMode: 'home',
  searchQuery: '',
  selectedProduct: null,
  uiConfig: DEFAULT_UI_CONFIG,
  navigation: null,
  searchRecommendation: '',
  searchResults: [],
  products: [],
  productsLoaded: false,
  sceneProject: null,
  sceneProjectLoaded: false,
  charging: DEFAULT_CHARGING_STATE,
  agent: DEFAULT_AGENT_STATE,
  chargingStatus: '',
  chargingMessage: '',

  setRetailTemplate: (template) => set({ retailTemplate: template }),
  setMenuTemplate: (template) => set({ menuTemplate: template }),
  setPromoTemplate: (template) => set({ promoTemplate: template }),
  setCurrentMode: (mode) => set({ currentMode: mode }),
  setSearchQuery: (query) => set({ searchQuery: query }),
  setSelectedProduct: (product) => set({ selectedProduct: product }),
  setUiConfig: (config) => set((state) => ({ uiConfig: { ...state.uiConfig, ...config } })),
  setNavigation: (navigation) => set({ navigation }),
  startNavigation: (destination, estimatedTime, distance) => set({
    currentMode: 'navigating',
    navigation: {
      destination,
      isNavigating: true,
      estimatedTime,
      distance,
    },
  }),
  setSearchRecommendation: (recommendation) => set({ searchRecommendation: recommendation }),
  setSearchResults: (results) => set({ searchResults: results }),
  applyRetailSearchPayload: ({ results, query, recommendation, mode = 'retail' }) => set({
    searchResults: results,
    searchQuery: query,
    searchRecommendation: recommendation,
    currentMode: mode,
  }),
  setProducts: (products) => set({ products, productsLoaded: true }),
  setProductsLoaded: (loaded) => set({ productsLoaded: loaded }),
  setSceneProject: (project) => set({ sceneProject: project, sceneProjectLoaded: true }),
  setChargingState: (chargingState) => set((state) => {
    const charging = { ...state.charging, ...chargingState };
    if (isSameChargingState(state.charging, charging)) {
      return state;
    }
    return {
      charging,
      chargingStatus: charging.status,
      chargingMessage: charging.message,
    };
  }),
  resetChargingState: (force = false) => set((state) => {
    if (!force && (state.charging.isCharging || state.charging.isNavigatingToCharger)) {
      return state;
    }
    if (isSameChargingState(state.charging, DEFAULT_CHARGING_STATE)) {
      return state;
    }
    return {
      charging: DEFAULT_CHARGING_STATE,
      chargingStatus: '',
      chargingMessage: '',
    };
  }),
  setChargingStatus: (status, message) => set((state) => ({
    charging: {
      ...state.charging,
      status,
      message,
      isCharging: status === 'charging',
      isNavigatingToCharger: status === 'navigating_to_charger' || status === 'charge_obstacle',
    },
    chargingStatus: status,
    chargingMessage: message,
  })),
  setAgentStatus: (status, message) => set((state) => ({
    agent: {
      ...state.agent,
      status,
      message,
    },
  })),
  setListeningGate: (gateOpen, personVisible) => set((state) => ({
    agent: {
      ...state.agent,
      gateOpen,
      personVisible,
    },
  })),
}));
