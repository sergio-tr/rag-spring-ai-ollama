import proxy from "./src/proxy";

export default proxy;

export const config = {
  matcher: ["/", "/(en|es)/:path*"],
};
