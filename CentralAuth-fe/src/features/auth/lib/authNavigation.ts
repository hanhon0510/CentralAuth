const transientAuthParams = ['email', 'token']

export function authPathWithSearch(
  path: string,
  search: string,
  params: Record<string, string | undefined> = {},
) {
  const searchParams = new URLSearchParams(search)

  Object.entries(params).forEach(([key, value]) => {
    if (value) {
      searchParams.set(key, value)
    } else {
      searchParams.delete(key)
    }
  })

  const nextSearch = searchParams.toString()
  return nextSearch ? `${path}?${nextSearch}` : path
}

export function authPathWithoutTransientParams(path: string, search: string) {
  const searchParams = new URLSearchParams(search)
  transientAuthParams.forEach((key) => searchParams.delete(key))

  const nextSearch = searchParams.toString()
  return nextSearch ? `${path}?${nextSearch}` : path
}
