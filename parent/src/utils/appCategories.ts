export const APP_CATEGORIES = [
  {id: 'ALL', label: 'All apps'},
  {id: 'GAME', label: 'Games'},
  {id: 'SOCIAL', label: 'Social'},
  {id: 'ENTERTAINMENT', label: 'Entertainment'},
  {id: 'PRODUCTIVITY', label: 'Productivity'},
  {id: 'NEWS', label: 'News'},
  {id: 'OTHER', label: 'Other'},
] as const;

export const WEEKDAYS = [
  {key: 'SUN', label: 'Sunday'},
  {key: 'MON', label: 'Monday'},
  {key: 'TUE', label: 'Tuesday'},
  {key: 'WED', label: 'Wednesday'},
  {key: 'THU', label: 'Thursday'},
  {key: 'FRI', label: 'Friday'},
  {key: 'SAT', label: 'Saturday'},
] as const;

export type WeekdayKey = (typeof WEEKDAYS)[number]['key'];

export type WeeklySchedule = Record<
  WeekdayKey,
  {enabled: boolean; limitMinutes: number}
>;

export function defaultWeeklySchedule(defaultMinutes: number): WeeklySchedule {
  return WEEKDAYS.reduce((acc, day) => {
    acc[day.key] = {enabled: true, limitMinutes: defaultMinutes};
    return acc;
  }, {} as WeeklySchedule);
}
