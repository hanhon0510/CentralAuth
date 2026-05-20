export function parseLines(value: string) {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
}

export function hasDuplicateLines(values: string[]) {
  return new Set(values).size !== values.length
}
