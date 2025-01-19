import js from "@eslint/js";
import globals from "globals";
import stylistic from "@stylistic/eslint-plugin";
import tseslint from "typescript-eslint";

export default tseslint.config(
    js.configs.recommended,
    tseslint.configs.recommended,
    {
        plugins: {
            "@stylistic": stylistic
        },
        languageOptions: {
            globals: {
                channel: "readonly",
                ...globals.browser
            },
            ecmaVersion: 2022,
            sourceType: "module"
        },
        rules: {
            "no-var": ["error"],
            "@stylistic/brace-style": ["error", "1tbs"],
            "@stylistic/indent": ["error", 4],
            "@stylistic/linebreak-style": ["error", "unix"],
            "@stylistic/quotes": ["error", "double"],
            "@stylistic/semi": ["error", "always"],
        },

    },
    {
        ignores: [
            "app/build/",
            "app/src/*/assets/viewer",
            "build/",
            "releases/"
        ]
    }
);
