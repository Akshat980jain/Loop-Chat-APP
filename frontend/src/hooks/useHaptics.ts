import { Haptics, ImpactStyle } from '@capacitor/haptics';

export const useHaptics = () => {
  const light = async () => {
    try {
      await Haptics.impact({ style: ImpactStyle.Light });
    } catch (error) {
      // Haptics not available on this platform
    }
  };

  const medium = async () => {
    try {
      await Haptics.impact({ style: ImpactStyle.Medium });
    } catch (error) {
      // Haptics not available on this platform
    }
  };

  const heavy = async () => {
    try {
      await Haptics.impact({ style: ImpactStyle.Heavy });
    } catch (error) {
      // Haptics not available on this platform
    }
  };

  const selection = async () => {
    try {
      await Haptics.selectionStart();
      await Haptics.selectionChanged();
      await Haptics.selectionEnd();
    } catch (error) {
      // Haptics not available on this platform
    }
  };

  return { light, medium, heavy, selection };
};
