/* مک: امضای ساده (ad-hoc) که برنامه روی مک‌های جدید (M1/M2/M3) اجرا بشه.
   امضای رسمی اپل نیست؛ بار اول باید راست‌کلیک > Open بزنی. */
const { execSync } = require('child_process');

exports.default = async function (context) {
  if (context.electronPlatformName !== 'darwin') return;
  const appPath = `${context.appOutDir}/${context.packager.appInfo.productFilename}.app`;
  execSync(`codesign --force --deep --sign - "${appPath}"`, { stdio: 'inherit' });
};
