import js from "@eslint/js";
import pluginVue from "eslint-plugin-vue";
import globals from "globals";
import tseslint from "typescript-eslint";

export default tseslint.config(
    {
        ignores: ["**/dist/**", "**/node_modules/**", "**/coverage/**"],
    },
    js.configs.recommended,
    ...tseslint.configs.recommended,
    ...pluginVue.configs["flat/recommended"],
    {
        files: ["**/*.{ts,vue}"],
        languageOptions: {
            globals: {
                ...globals.browser,
                ...globals.es2025,
            },
            parserOptions: {
                parser: tseslint.parser,
                extraFileExtensions: [".vue"],
            },
        },
        rules: {
            "@typescript-eslint/no-explicit-any": "off",
            "vue/multi-word-component-names": "off",
            "vue/max-attributes-per-line": "off",
            "vue/singleline-html-element-content-newline": "off",
            "vue/html-indent": "off",
            "vue/attributes-order": "off",
            "vue/html-closing-bracket-newline": "off",
            "vue/html-closing-bracket-spacing": "off",
            "vue/html-self-closing": "off",
            "vue/multiline-html-element-content-newline": "off",
            "vue/mustache-interpolation-spacing": "off",
        },
    },
);
