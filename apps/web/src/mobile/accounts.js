// Frontend-only profile directory. Passwords are never included in this store.
export const PROFILE_KEY = "chama-ui-profiles-v1";
export const digits = (value) => value.replace(/\D/g, "");
export function findProfile(profiles, query) {
  const value = query.trim().toLowerCase();
  if (!value) return undefined;
  return profiles.find(
    (p) =>
      p.handle.toLowerCase() === value ||
      (p.phone && /^[-+\d\s()]+$/.test(value) && p.phone === digits(value)),
  );
}
export function readProfiles() {
  try {
    const data = JSON.parse(localStorage.getItem(PROFILE_KEY) || "[]");
    if (!Array.isArray(data)) return [];
    return data
      .filter(
        (p) =>
          p &&
          typeof p.id === "string" &&
          typeof p.name === "string" &&
          typeof p.handle === "string" &&
          typeof p.phone === "string" &&
          ["driver", "recorder"].includes(p.role) &&
          Array.isArray(p.friendIds),
      )
      .map(({ id, name, handle, phone, role, friendIds }) => ({
        id,
        name,
        handle,
        phone,
        role,
        friendIds: friendIds.filter((v) => typeof v === "string"),
      }));
  } catch {
    return [];
  }
}
export function connectProfiles(profiles, ownerId, otherId) {
  const owner = profiles.find((p) => p.id === ownerId),
    other = profiles.find((p) => p.id === otherId);
  if (!owner || !other) return { error: "먼저 계정을 만들어주세요." };
  if (ownerId === otherId)
    return { error: "내 계정은 친구로 추가할 수 없습니다." };
  if (owner.role === other.role)
    return { error: "운전자와 녹음자 계정을 서로 연결해주세요." };
  if (owner.friendIds.includes(otherId))
    return { error: "이미 연결된 친구입니다." };
  return {
    profiles: profiles.map((p) =>
      p.id === ownerId
        ? { ...p, friendIds: [...p.friendIds, otherId] }
        : p.id === otherId
          ? { ...p, friendIds: [...new Set([...p.friendIds, ownerId])] }
          : p,
    ),
  };
}

export const roleName = (role) => (role === "driver" ? "운전자" : "녹음자");
