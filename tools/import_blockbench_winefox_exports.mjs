import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(import.meta.dirname, "..");
const DEFAULT_ANIMATION = "E:/Documents/Tencent Files/2868618204/FileRecv/magical_winefox_boss.animation (2).json";
const DEFAULT_SPEAR_GEO = "E:/Documents/Tencent Files/2868618204/FileRecv/winefox_spear_projectile.geo.json";
const animationInput = path.resolve(process.argv[2] ?? DEFAULT_ANIMATION);
const spearGeoInput = path.resolve(process.argv[3] ?? DEFAULT_SPEAR_GEO);
const assetRoot = path.join(ROOT, "src/main/resources/assets/touhou_little_maid_spell");
const animationOutput = path.join(assetRoot, "animations/magical_winefox_boss.animation.json");
const spearGeoOutput = path.join(assetRoot, "geo/winefox_spear_projectile.geo.json");
const animationSourceOutput = path.join(ROOT, "model_sources/magical_winefox_boss.blockbench.animation.json");
const spearGeoSourceOutput = path.join(ROOT, "model_sources/winefox_spear_projectile.blockbench.geo.json");
const animationReportOutput = path.join(ROOT, "model_sources/magical_winefox_boss.blockbench-import-report.json");

function splitArguments(text) {
    const args = [];
    let start = 0;
    let depth = 0;
    let quote = null;
    for (let index = 0; index < text.length; index++) {
        const char = text[index];
        if (quote) {
            if (char === quote && text[index - 1] !== "\\") quote = null;
        } else if (char === "'" || char === '"') {
            quote = char;
        } else if (char === "(") {
            depth++;
        } else if (char === ")") {
            depth--;
        } else if (char === "," && depth === 0) {
            args.push(text.slice(start, index).trim());
            start = index + 1;
        }
    }
    args.push(text.slice(start).trim());
    return args;
}

function replaceSecondOrder(expression) {
    const functionName = "ysm.second_order";
    let output = expression;
    let index = output.indexOf(`${functionName}(`);
    while (index >= 0) {
        const open = index + functionName.length;
        let depth = 0;
        let quote = null;
        let end = open;
        for (; end < output.length; end++) {
            const char = output[end];
            if (quote) {
                if (char === quote && output[end - 1] !== "\\") quote = null;
            } else if (char === "'" || char === '"') {
                quote = char;
            } else if (char === "(") {
                depth++;
            } else if (char === ")" && --depth === 0) {
                end++;
                break;
            }
        }
        const args = splitArguments(output.slice(open + 1, end - 1));
        const key = args[0]?.replace(/^['"]|['"]$/g, "");
        if (key !== "鞘翅yaw") {
            throw new Error(`Unsupported ysm.second_order key ${JSON.stringify(key)}`);
        }
        output = `${output.slice(0, index)}variable.winefox_body_yaw${output.slice(end)}`;
        index = output.indexOf(`${functionName}(`, index + 1);
    }
    return output;
}

function normalizeExpression(raw) {
    if (!/ysm\./i.test(raw)) return raw;

    let expression = String(raw).trim().replace(/;$/, "");
    expression = replaceSecondOrder(expression)
        .replace(/ysm\.input_vertical/gi, "variable.winefox_input_vertical")
        .replace(/ysm\.head_yaw/gi, "variable.winefox_head_yaw")
        .replace(/ysm\.head_pitch/gi, "variable.winefox_head_pitch")
        .replace(/ysm\.has_helmet/gi, "variable.winefox_has_helmet")
        .replace(/ysm\.has_mainhand/gi, "variable.winefox_has_mainhand")
        .replace(/ysm\.has_offhand/gi, "variable.winefox_has_offhand");

    const movingBackward = "math.clamp((-variable.winefox_input_vertical - 0.05) * 1000, 0, 1)";
    expression = expression.replace(
        /\(?\s*variable\.winefox_input_vertical\s*<\s*-0\.05\s*\?\s*(-?\d+(?:\.\d+)?)\s*:\s*(-?\d+(?:\.\d+)?)\s*\)?/g,
        (_match, whenTrue, whenFalse) => {
            const trueValue = Number(whenTrue);
            const falseValue = Number(whenFalse);
            return `((${movingBackward}) * ${trueValue - falseValue} + ${falseValue})`;
        }
    );

    if (/[?]|==|!=|&&|\|\||ysm\./i.test(expression)) {
        throw new Error(`Unsupported Blockbench Molang expression: ${expression}`);
    }
    return expression;
}

function normalizeAnimation(animation) {
    if (Array.isArray(animation)) return animation.map(normalizeAnimation);
    if (animation && typeof animation === "object") {
        return Object.fromEntries(Object.entries(animation).map(([key, value]) => [key, normalizeAnimation(value)]));
    }
    return typeof animation === "string" ? normalizeExpression(animation) : animation;
}

function copyFile(source, destination) {
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    fs.copyFileSync(source, destination);
}

const animationBuffer = fs.readFileSync(animationInput);
const spearGeoBuffer = fs.readFileSync(spearGeoInput);
const animation = JSON.parse(animationBuffer.toString("utf8"));
const spearGeo = JSON.parse(spearGeoBuffer.toString("utf8"));
if (!animation.animations || Object.keys(animation.animations).length !== 50) {
    throw new Error("Expected the Blockbench animation export to contain exactly 50 animations");
}
if (spearGeo["minecraft:geometry"]?.[0]?.description?.identifier !== "geometry.winefox_spear_projectile") {
    throw new Error("Unexpected spear Blockbench geometry identifier");
}

// Keep Blockbench's authored geometry and animation tracks. Only normalize the
// YSM-only Molang expressions that GeckoLib 4.7 cannot parse itself.
const runtimeAnimation = {
    format_version: animation.format_version,
    animations: normalizeAnimation(animation.animations)
};
assertAuthoredDataPreserved(animation, runtimeAnimation);
copyFile(animationInput, animationSourceOutput);
copyFile(spearGeoInput, spearGeoSourceOutput);
fs.writeFileSync(animationOutput, JSON.stringify(runtimeAnimation, null, 2));
fs.writeFileSync(spearGeoOutput, spearGeoBuffer);

const report = {
    sourceAnimation: animationInput,
    sourceSpearGeometry: spearGeoInput,
    animationSha256: crypto.createHash("sha256").update(animationBuffer).digest("hex"),
    spearGeometrySha256: crypto.createHash("sha256").update(spearGeoBuffer).digest("hex"),
    animationCount: Object.keys(animation.animations).length,
    normalizedExpressions: countYsmExpressions(animation.animations)
};
fs.writeFileSync(animationReportOutput, JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));

function countYsmExpressions(value) {
    if (typeof value === "string") return /ysm\./i.test(value) ? 1 : 0;
    if (Array.isArray(value)) return value.reduce((count, item) => count + countYsmExpressions(item), 0);
    if (value && typeof value === "object") {
        return Object.values(value).reduce((count, item) => count + countYsmExpressions(item), 0);
    }
    return 0;
}

function assertAuthoredDataPreserved(source, runtime, location = "root") {
    if (typeof source === "string") {
        if (!/ysm\./i.test(source) && source !== runtime) {
            throw new Error(`Unexpected authored string change at ${location}`);
        }
        return;
    }
    if (Array.isArray(source)) {
        if (!Array.isArray(runtime) || source.length !== runtime.length) {
            throw new Error(`Unexpected authored array change at ${location}`);
        }
        source.forEach((value, index) =>
            assertAuthoredDataPreserved(value, runtime[index], `${location}[${index}]`));
        return;
    }
    if (source && typeof source === "object") {
        if (!runtime || typeof runtime !== "object" || Array.isArray(runtime)) {
            throw new Error(`Unexpected authored object change at ${location}`);
        }
        const sourceKeys = Object.keys(source);
        const runtimeKeys = Object.keys(runtime);
        if (sourceKeys.length !== runtimeKeys.length
                || sourceKeys.some((key, index) => key !== runtimeKeys[index])) {
            throw new Error(`Unexpected authored keys change at ${location}`);
        }
        sourceKeys.forEach((key) =>
            assertAuthoredDataPreserved(source[key], runtime[key], `${location}.${key}`));
        return;
    }
    if (!Object.is(source, runtime)) {
        throw new Error(`Unexpected authored value change at ${location}`);
    }
}
