# GlimDroid

An unofficial app for [Glimesh.tv](https://glimesh.tv) - a real-time livestreaming platform.

## Installation

Visit
the [Google Play Store](https://play.google.com/store/apps/details?id=com.danielstiner.glimdroid),
or run locally using [android studio](https://developer.android.com/studio).

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would
like to change.

Please make sure to update tests as appropriate.

### Generating Apollo Schema

```shell
./gradlew :app:downloadApolloSchema --endpoint='https://glimesh.tv/api/graph' --schema=`pwd`/app/src/main/graphql/com/danielstiner/glimdroid/apollo/schema.graphqls --header="Authorization: Bearer $token"
```

## License

[MIT](https://choosealicense.com/licenses/mit/)
