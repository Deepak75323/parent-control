import type {NavigationState, PartialState} from '@react-navigation/native';

type NavState = NavigationState | PartialState<NavigationState> | undefined;

export function getActiveRouteName(state: NavState): string | undefined {
  if (!state || !('routes' in state) || !state.routes?.length) {
    return undefined;
  }
  const index = state.index ?? state.routes.length - 1;
  const route = state.routes[index];
  if (route.state) {
    return getActiveRouteName(route.state as NavState);
  }
  return route.name;
}
