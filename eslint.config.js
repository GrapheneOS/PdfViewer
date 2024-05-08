import js from "@eslint/js";
import globals from "globals";

export default [
    js.configs.recommended,
    {
        languageOptions: {
            globals: {
                channel: "readonly",
                ...globals.browser
            },
            ecmaVersion: 2022,
            sourceType: "module"
        },
        rules: {
            indent: ["error", 4],
            "linebreak-style": ["error", "unix"],
            quotes: ["error", "double"],
            semi: ["error", "always"],
            "no-unused-vars": ["error", {caughtErrors: "none"}],
            "no-var": ["error"]
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
];
