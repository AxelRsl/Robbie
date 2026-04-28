// Mapeo de iconos basado en ROBBIE-PANEL (Lucide React)
// Usando react-native-vector-icons/MaterialIcons como equivalente

export const IconMap = {
  // Iconos principales del panel
  robot: 'android',                    // Bot
  shieldCheck: 'verified-user',        // ShieldCheck (Robot QA)
  messageSquare: 'chat',               // MessageSquare (Saludos)
  clapperboard: 'movie',               // Clapperboard (Módulos)
  messageSquareText: 'question-answer', // MessageSquareText (Knowledge)
  map: 'map',                          // Map (Tour Guide)
  layers: 'layers',                    // Layers (Robot Maps)
  sparkles: 'auto-awesome',            // Sparkles (Skill Center)
  wrench: 'build',                     // Wrench (Skill Management)
  calendarClock: 'schedule',           // CalendarClock (Schedule)
  layoutDashboard: 'dashboard',        // LayoutDashboard (Home Settings)
  shoppingCart: 'shopping-cart',       // ShoppingCart (Retail)
  image: 'image',                      // Image (Exhibits)
  wifi: 'wifi',                        // Wifi (IoT)
  mic: 'mic',                          // Mic (Voice Report)
  code: 'code',                        // Code (Agent Dev)
  settings: 'settings',                // Settings
  users: 'people',                     // Users (User Management)
  
  // Iconos de navegación y UI
  chevronDown: 'keyboard-arrow-down',
  chevronRight: 'keyboard-arrow-right',
  chevronLeft: 'keyboard-arrow-left',
  chevronUp: 'keyboard-arrow-up',
  
  // Iconos de acciones
  search: 'search',
  close: 'close',
  check: 'check',
  add: 'add',
  remove: 'remove',
  edit: 'edit',
  delete: 'delete',
  refresh: 'refresh',
  
  // Iconos de estado
  loading: 'hourglass-empty',
  success: 'check-circle',
  error: 'error',
  warning: 'warning',
  info: 'info',
  
  // Iconos específicos del robot
  navigation: 'navigation',
  location: 'location-on',
  battery: 'battery-std',
  volume: 'volume-up',
  volumeOff: 'volume-off',
  
  // Iconos de productos
  product: 'inventory',
  category: 'category',
  price: 'attach-money',
  discount: 'local-offer',
  
  // Iconos de modo
  retail: 'store',
  exhibition: 'museum',
  tour: 'tour',
  idle: 'pause-circle-outline',
};

// Tamaños estándar de iconos (ajustados para pantalla 549x309 dp)
export const IconSizes = {
  xs: 8,
  sm: 12,
  md: 16,
  lg: 20,
  xl: 24,
  xxl: 32,
};

// Función helper para obtener icono
export const getIcon = (name: keyof typeof IconMap): string => {
  return IconMap[name] || IconMap.settings;
};
