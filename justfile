build_dev: gomobile
    just gomobile debug
    ./gradlew installDebug

gomobile tags='_':
    #!/usr/bin/env fish
    # Check if Go files are newer than the AAR
    if test ! -f app/libs/backend.aar -o backend/backend/backend.go -nt app/libs/backend.aar
        echo "binding gomobile..."
        cd backend/backend
        gomobile bind -tags='{{tags}},libsecp256k1,sqlite_math_functions' -androidapi 26 -target android -o ../../app/libs/backend.aar
    end

build_release: gomobile
    ./gradlew assembleRelease
    ./gradlew installRelease

emulator: gomobile
    ./gradlew assembleDebug
    ./gradlew installDebug

emulator_release: gomobile
    ./gradlew assembleRelease
    ./gradlew installRelease
