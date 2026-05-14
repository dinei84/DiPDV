export class ApiError extends Error {
  constructor(
    public status: number,
    public body: any,
    message: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}
