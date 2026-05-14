import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  Image,
  ImageBackground,
  TouchableOpacity,
  StyleSheet,
  NativeModules,
} from 'react-native';
import { useAppStore } from '@/stores/useAppStore';
import { CloudApi } from '@/services/CloudApi';
import { useTheme } from '@/contexts/ThemeContext';
import { createStyles, GlobalStyles } from '@/theme/styles';
import { Icon } from '@/components/ui/Icon';
import {
  ThreeCardsTemplate,
  FourCardsTemplate,
  FiveCardsTemplate,
  BubbleLeftTemplate,
  BubbleBottomTemplate,
  FunctionalGridTemplate,
} from '@/components/templates';
import type { SceneFunction, SceneProject, TemplateType } from '@/types';

const { RobotSkillModule } = NativeModules;

// Funciones por defecto cuando no hay proyecto de escena configurado
const DEFAULT_FUNCTIONS: SceneFunction[] = [
  {
    id: 'retail',
    projectId: 'default',
    name: 'Productos',
    icon: 'shoppingCart',
    activationCommand: 'retail',
    orderIndex: 0,
    color: '#E4027C',
    description: 'Catalogo de productos',
  },
  {
    id: 'promo',
    projectId: 'default',
    name: 'Promociones',
    icon: 'sparkles',
    activationCommand: 'promo',
    orderIndex: 1,
    color: '#00BFA5',
    description: 'Ofertas especiales',
  },
  {
    id: 'navigate',
    projectId: 'default',
    name: 'Navegar',
    icon: 'navigation',
    activationCommand: 'navigating',
    orderIndex: 2,
    color: '#F472B6',
    description: 'Ir a ubicacion',
  },
  {
    id: 'search',
    projectId: 'default',
    name: 'Buscar',
    icon: 'search',
    activationCommand: 'retail',
    orderIndex: 3,
    color: '#10B981',
    description: 'Busqueda por voz',
  },
];

export const MenuScreen: React.FC = () => {
  const { setCurrentMode, sceneProject, sceneProjectLoaded, setSceneProject } = useAppStore();
  const [loading, setLoading] = useState(true);
  const { theme } = useTheme();
  const styles = createStyles(theme);

  const loadSceneProject = async () => {
    try {
      const project = await CloudApi.getActiveSceneProject();
      console.log('[MenuScreen] Proyecto de escena cargado:', project?.name || 'ninguno', '- template:', project?.templateType);
      setSceneProject(project);
    } catch (error) {
      console.error('[MenuScreen] Error cargando proyecto de escena:', error);
      if (!sceneProjectLoaded) setSceneProject(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSceneProject();
  }, []);

  const mapTextCommandToAction = (text: string): string | null => {
    const normalized = text.trim().toLowerCase();

    if (
      normalized === 've a cargar'
      || normalized === 'ir a cargar'
      || normalized === 'vete a cargar'
      || normalized === 'carga tu bateria'
      || normalized === 'carga tu batería'
      || normalized === 'recarga'
      || normalized === 've al cargador'
    ) {
      return 'com.robbie.action.GO_CHARGE';
    }

    if (
      normalized === 'deja de cargar'
      || normalized === 'detener carga'
      || normalized === 'deten la carga'
      || normalized === 'detén la carga'
      || normalized === 'sal del cargador'
      || normalized === 'ya no cargues'
    ) {
      return 'com.robbie.action.STOP_CHARGE';
    }

    return null;
  };

  const handleFunctionPress = async (fn: SceneFunction) => {
    const command = fn.activationCommand;
    console.log('[MenuScreen] Funcion presionada:', fn.name, '- comando:', command);

    if (command.startsWith('action:')) {
      const actionName = command.substring(7).trim();
      if (!actionName || !RobotSkillModule) {
        return;
      }
      try {
        await RobotSkillModule.executeAction(actionName, '{}');
      } catch (error) {
        console.error('[MenuScreen] Error ejecutando action nativa:', error);
      }
      return;
    }

    if (command.startsWith('com.robbie.action.')) {
      if (!RobotSkillModule) {
        return;
      }
      try {
        await RobotSkillModule.executeAction(command, '{}');
      } catch (error) {
        console.error('[MenuScreen] Error ejecutando action directa:', error);
      }
      return;
    }

    // TTS commands: send text to AgentOS and stay on menu
    if (command.startsWith('tts:')) {
      const text = command.substring(4).trim();
      const mappedAction = mapTextCommandToAction(text);
      if (mappedAction) {
        if (!RobotSkillModule) {
          return;
        }
        try {
          await RobotSkillModule.executeAction(mappedAction, '{}');
        } catch (error) {
          console.error('[MenuScreen] Error convirtiendo comando TTS a action:', error);
        }
        return;
      }
      console.log('[MenuScreen] Enviando comando TTS al agente:', text);
      try {
        await fetch('http://127.0.0.1:8080/api/scene-projects/agent-query', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ text }),
        });
      } catch (error) {
        console.error('[MenuScreen] Error enviando query al agente:', error);
      }
      return; // Stay on menu screen
    }

    setCurrentMode(command);
  };

  if (loading && !sceneProjectLoaded) {
    return (
      <View style={[styles.container, GlobalStyles.center]}>
        <Icon name="loading" size="xl" color={theme.colors.primary} />
        <Text style={[styles.body, { marginTop: 16 }]}>Cargando...</Text>
      </View>
    );
  }

  const functions = sceneProject?.functions?.length
    ? sceneProject.functions
    : DEFAULT_FUNCTIONS;

  const templateType: TemplateType = sceneProject?.templateType || 'FUNCTIONAL_GRID';

  const renderTemplate = () => {
    switch (templateType) {
      case 'THREE_CARDS':
        return <ThreeCardsTemplate functions={functions} onPress={handleFunctionPress} />;
      case 'FOUR_CARDS':
        return <FourCardsTemplate functions={functions} onPress={handleFunctionPress} />;
      case 'FIVE_CARDS':
        return <FiveCardsTemplate functions={functions} onPress={handleFunctionPress} />;
      case 'BUBBLE_LEFT':
        return <BubbleLeftTemplate functions={functions} onPress={handleFunctionPress} />;
      case 'BUBBLE_BOTTOM':
        return <BubbleBottomTemplate functions={functions} onPress={handleFunctionPress} />;
      case 'FUNCTIONAL_GRID':
      default:
        return <FunctionalGridTemplate functions={functions} onPress={handleFunctionPress} />;
    }
  };

  const renderTitle = () => {
    if (!sceneProject) return null;

    if (sceneProject.titleType === 'IMAGE' && sceneProject.titleImageUrl) {
      return (
        <View style={localStyles.titleBar}>
          <Image
            source={{ uri: sceneProject.titleImageUrl }}
            style={localStyles.titleImage}
            resizeMode="contain"
          />
        </View>
      );
    }

    if (sceneProject.titleType === 'TEXT' && sceneProject.titleText) {
      return (
        <View style={localStyles.titleBar}>
          <Text style={[localStyles.titleText, { color: theme.colors.onBackground }]}>
            {sceneProject.titleText}
          </Text>
        </View>
      );
    }

    return null;
  };

  const hasBackground = sceneProject?.backgroundImageUrl;

  const content = (
    <View style={[styles.container, { backgroundColor: hasBackground ? 'transparent' : theme.colors.background }]}>
      {renderTitle()}
      {renderTemplate()}
    </View>
  );

  if (hasBackground) {
    return (
      <ImageBackground
        source={{ uri: sceneProject!.backgroundImageUrl }}
        style={localStyles.backgroundImage}
        resizeMode="cover"
      >
        <View style={localStyles.backgroundOverlay} />
        {content}
      </ImageBackground>
    );
  }

  return content;
};

const localStyles = StyleSheet.create({
  titleBar: {
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 4,
    alignItems: 'center',
  },
  titleText: {
    fontSize: 16,
    fontWeight: '700',
    letterSpacing: -0.3,
  },
  titleImage: {
    height: 32,
    maxWidth: 200,
  },
  backgroundImage: {
    flex: 1,
  },
  backgroundOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.15)',
  },
});
