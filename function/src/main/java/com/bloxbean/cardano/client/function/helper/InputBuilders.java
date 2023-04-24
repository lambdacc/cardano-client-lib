package com.bloxbean.cardano.client.function.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.config.Configuration;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.TxInputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.api.util.UtxoUtil;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Provides helper methods to create {@link TxInputBuilder} function to build a list of <code>TransactionInput</code> from a list of <code>TransactionOutput</code>
 */
@Slf4j
public class InputBuilders {

    /**
     * Function to create inputs and change output from list of <code>{@link TransactionOutput}</code> for a sender
     *
     * @param sender        Address to select utxo from
     * @param changeAddress change address
     * @return <code>{@link TxInputBuilder}</code> function
     * @throws TxBuildException if not enough utxos available at sender address
     * @throws ApiRuntimeException if api error
     */
    public static TxInputBuilder createFromSender(String sender, String changeAddress) {

        return ((context, outputs) -> {
            if (outputs == null || outputs.size() == 0)
                throw new TxBuildException("No output found. UtxoSelector transformer should be called after OutputTransformer");
            //Total value required
            Value value = Value.builder().coin(BigInteger.ZERO).multiAssets(new ArrayList<>()).build();
            value = outputs.stream()
                    .map(output -> output.getValue())
                    .reduce(value, (value1, value2) -> value1.plus(value2));

            //Check if mint value is there in context. Then we need to ignore mint outputs from total value
            List<MultiAsset> mintMultiAssets = context.getMintMultiAssets();
            if (mintMultiAssets != null && mintMultiAssets.size() > 0)
                value = value.minus(new Value(BigInteger.ZERO, mintMultiAssets));

            Set<Utxo> utxoSet = getUtxosForValue(context, sender, value, Collections.EMPTY_SET);

            List<TransactionInput> _inputs = utxoSet.stream()
                    .map(utxo -> new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex()))
                    .collect(Collectors.toList());

            if (utxoSet != null && !utxoSet.isEmpty()) {
                //Copy assets to change address
                TransactionOutput changeOutput = new TransactionOutput(changeAddress, new Value(BigInteger.ZERO, new ArrayList<>()));
                utxoSet.stream().forEach(utxo -> {
                    UtxoUtil.copyUtxoValuesToOutput(changeOutput, utxo);
                    context.addUtxo(utxo); //Add used utxos to context
                });

                //Substract output values from change
                Value changedValue = changeOutput.getValue().minus(value);
                changeOutput.setValue(changedValue);
                BigInteger additionalLovelace = MinAdaCheckers.minAdaChecker().apply(context, changeOutput);

                if (additionalLovelace.compareTo(BigInteger.ZERO) == 1) { //Need more inputs
                    Value additionalValue = Value.builder()
                            .coin(additionalLovelace).build();

                    Set<Utxo> additionalUtxos = getUtxosForValue(context, sender, additionalValue, utxoSet);

                    List<TransactionInput> additionalInputs = additionalUtxos.stream()
                            .map(utxo -> new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex()))
                            .collect(Collectors.toList());

                    additionalUtxos.stream().forEach(utxo -> {
                        UtxoUtil.copyUtxoValuesToOutput(changeOutput, utxo);
                        context.addUtxo(utxo); //Add used utxos to context
                    });

                    _inputs.addAll(additionalInputs);
                }

                return new TxInputBuilder.Result(_inputs, List.of(changeOutput));
            } else {
                //Something wrong
                log.warn("Empty input. In normal case, this should not happen.");
                return new TxInputBuilder.Result(Collections.EMPTY_LIST, Collections.EMPTY_LIST);
            }
        });
    }

    private static Set<Utxo> getUtxosForValue(TxBuilderContext context, String sender, Value value, Set<Utxo> excludeUtxos) {
        Set<Utxo> utxoSet = new HashSet<>();

        List<Utxo> lovelaceUtxos;
        try {
            if (value.getCoin() != null && !value.getCoin().equals(BigInteger.ZERO)) {
                lovelaceUtxos = context.getUtxoSelectionStrategy().selectUtxos(sender, LOVELACE, value.getCoin(), excludeUtxos);
            } else {
                lovelaceUtxos = Collections.EMPTY_LIST;
            }
        } catch (ApiException apiException) {
            throw new ApiRuntimeException(apiException);
        }

        List<Utxo> multiAssetUtoxs = value.getMultiAssets().stream()
                .flatMap(multiAsset -> multiAsset.getAssets().stream()
                        .filter(asset -> !asset.getValue().equals(BigInteger.ZERO)) //TODO -- Check if need to remove
                        .map(asset -> new Tuple<String, Asset>(multiAsset.getPolicyId(), asset)))
                .map(tuple -> {
                    String unit = AssetUtil.getUnit(tuple._1, tuple._2);
                    try {
                        List<Utxo> utxoList = (List<Utxo>) context.getUtxoSelectionStrategy().selectUtxos(sender, unit, tuple._2.getValue(), excludeUtxos);
                        if (utxoList != null && utxoList.size() != 0)
                            return utxoList;
                        else
                            throw new ApiRuntimeException(String.format("No utxo found at address=%s, unit= %s, value=%s", sender, unit, tuple._2.getValue()));
                    } catch (ApiException apiException) {
                        throw new ApiRuntimeException("Error fetching utxos for qty: " + tuple._2 + ", and asset: " + tuple._2, apiException);
                    }
                })
                .flatMap(list -> list.stream())
                .collect(Collectors.toList());

        utxoSet.addAll(lovelaceUtxos);
        utxoSet.addAll(multiAssetUtoxs);
        return utxoSet;
    }


    /**
     * Function to create inputs and change output from list of <code>{@link Utxo}</code>
     *
     * @param utxos         list of <code>{@link Utxo}</code>
     * @param changeAddress change address
     * @return <code>{@link TxInputBuilder}</code> function
     */
    public static TxInputBuilder createFromUtxos(List<Utxo> utxos, String changeAddress) {
        return createFromUtxos(utxos, changeAddress, null);
    }

    /**
     * Function to create inputs and change output from list of <code>{@link Utxo}</code>
     *
     * @param utxos         list of <code>{@link Utxo}</code>
     * @param changeAddress change address
     * @param datum         datum object. It can be an instance of <code>{@link PlutusData}</code> or object of a custom class with <code>@{@link com.bloxbean.cardano.client.plutus.annotation.Constr}</code> annotation
     *                      or one of the supported types <code>Integer, BigInteger, Long, byte[], String</code>
     * @return <code>{@link TxInputBuilder}</code> function
     * @throws CborRuntimeException                                                       if error during serialization of datum
     * @throws com.bloxbean.cardano.client.plutus.exception.PlutusDataConvertionException if datum cannot be converted to PlutusData
     */
    public static TxInputBuilder createFromUtxos(List<Utxo> utxos, String changeAddress, Object datum) {
        if (datum == null) {
            return createFromUtxos(utxos, changeAddress, null);
        } else {
            try {
                String datumHash = Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(datum).getDatumHash();
                return createFromUtxos(utxos, changeAddress, datumHash);
            } catch (CborException | CborSerializationException e) {
                throw new CborRuntimeException("Cbor serialization exeception ", e);
            }
        }
    }

    /**
     * Function to create inputs and change output from list of <code>{@link Utxo}</code>
     *
     * @param utxos         list of <code>{@link Utxo}</code>
     * @param changeAddress change address
     * @param datumHash     datum hash to add to change output
     * @return <code>{@link TxInputBuilder}</code> function
     */
    public static TxInputBuilder createFromUtxos(List<Utxo> utxos, String changeAddress, String datumHash) {
        return createFromUtxos(() -> utxos, changeAddress, datumHash);
    }

    /**
     * Function to create inputs and change output from list of <code>{@link Utxo}</code>
     *
     * @param supplier      Supplier function to provide a list of <code>{@link Utxo}</code>
     * @param changeAddress change address
     * @param datumHash     datum hash to add to change output
     * @return <code>{@link TxInputBuilder}</code> function
     */
    public static TxInputBuilder createFromUtxos(Supplier<List<Utxo>> supplier, String changeAddress, String datumHash) {
        return (context, outputs) -> {
            //Total value required
            Value value = Value.builder().coin(BigInteger.ZERO).multiAssets(new ArrayList<>()).build();
            value = outputs.stream()
                    .map(TransactionOutput::getValue)
                    .reduce(value, Value::plus);

            // Check if mint value is there in context. Then we need to ignore mint outputs from total value
            List<MultiAsset> mintMultiAssets = context.getMintMultiAssets();
            if (mintMultiAssets != null && !mintMultiAssets.isEmpty())
                value = value.minus(new Value(BigInteger.ZERO, mintMultiAssets));

            List<Utxo> utxos = supplier.get();

            List<TransactionInput> _inputs = utxos.stream()
                    .map(utxo -> new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex()))
                    .collect(Collectors.toList());

            List<TransactionOutput> changeOutputs = new ArrayList<>();
            if (changeAddress != null && !changeAddress.isEmpty()) {
                //Copy assets to change address
                TransactionOutput changeOutput = new TransactionOutput(changeAddress, new Value(BigInteger.ZERO, new ArrayList<>()));
                utxos.forEach(utxo -> {
                    UtxoUtil.copyUtxoValuesToOutput(changeOutput, utxo);
                    context.addUtxo(utxo); //Add used utxo to context
                });

                //Subtract output values from change
                Value changedValue = changeOutput.getValue().minus(value);
                changeOutput.setValue(changedValue);

                if (datumHash != null && !datumHash.isEmpty())
                    changeOutput.setDatumHash(HexUtil.decodeHexString(datumHash));

                if (!changeOutput.getValue().getCoin().equals(BigInteger.ZERO) ||
                        (changeOutput.getValue().getMultiAssets() != null && !changeOutput.getValue().getMultiAssets().isEmpty())) {
                    changeOutputs.add(changeOutput);
                }
            }

            return new TxInputBuilder.Result(_inputs, changeOutputs);
        };
    }

    /**
     * Function to create inputs from list of <code>{@link Utxo}</code>
     * @deprecated
     * This method should not be used as it doesn't calculate change output. Additional utxos are required to balance the
     * outputs.
     * <p>Use any of the other createFromUtxos method with a changeAddress</p>
     *
     * @param utxos list of <code>{@link Utxo}</code>
     * @return <code>{@link TxInputBuilder}</code> function
     */
    @Deprecated
    public static TxInputBuilder createFromUtxos(List<Utxo> utxos) {
        return (context, outputs) -> {
            List<TransactionInput> inputs = new ArrayList<>();
            utxos.forEach(utxo -> {
                TransactionInput input = TransactionInput.builder()
                        .transactionId(utxo.getTxHash())
                        .index(utxo.getOutputIndex())
                        .build();
                inputs.add(input);
                context.addUtxo(utxo);
            });

            return new TxInputBuilder.Result(inputs, Collections.EMPTY_LIST);
        };
    }

    /**
     * Function to create inputs from list of <code>{@link Utxo}</code>
     * @deprecated
     * This method should not be used as it doesn't calculate change output. Additional utxos are required to balance the
     * outputs.
     * <p>Use any of the other createFromUtxos method with a changeAddress</p>
     *
     * @param supplier Supplier function to provide <code>Utxo</code> list
     * @return <code>{@link TxInputBuilder}</code> function
     */
    @Deprecated
    public static TxInputBuilder createFromUtxos(Supplier<List<Utxo>> supplier) {
        return (context, outputs) -> {
            List<TransactionInput> inputs = new ArrayList<>();
            supplier.get().forEach(utxo -> {
                TransactionInput input = TransactionInput.builder()
                        .transactionId(utxo.getTxHash())
                        .index(utxo.getOutputIndex())
                        .build();
                inputs.add(input);
                context.addUtxo(utxo);
            });

            return new TxInputBuilder.Result(inputs, Collections.EMPTY_LIST);
        };
    }

    /**
     * Function to populate reference inputs using a supplier function
     * @param supplier - Supplier function to provide Utxo list
     * @return <code>{@link TxBuilder}</code> function
     */
    public static TxBuilder referenceInputsFromUtxos(Supplier<List<Utxo>> supplier) {
        return (context, transaction) -> {
            referenceInputsFromUtxos(supplier.get()).apply(context, transaction);
        };
    }

    /**
     * Function to populate reference inputs from list of utxos
     * @param utxos List of utxos for reference inputs
     * @return <code>{@link TxBuilder}</code> function
     */
    public static TxBuilder referenceInputsFromUtxos(List<Utxo> utxos) {
        return (context, transaction) -> {
            if (utxos == null || utxos.isEmpty())
                return;

            List<TransactionInput> referenceInputs = new ArrayList<>();
            utxos.forEach(utxo -> {
                TransactionInput input = TransactionInput.builder()
                        .transactionId(utxo.getTxHash())
                        .index(utxo.getOutputIndex())
                        .build();
                referenceInputs.add(input);
            });

            if (transaction.getBody().getReferenceInputs() == null)
                transaction.getBody().setReferenceInputs(referenceInputs);
            else
                transaction.getBody().getReferenceInputs().addAll(referenceInputs);
        };
    }

    /**
     * Function to populate reference inputs
     * @param referenceInputs List of inputs
     * @return <code>{@link TxBuilder}</code> function
     */
    public static TxBuilder referenceInputsFrom(List<TransactionInput> referenceInputs) {
        return (context, transaction) -> {
            if (transaction.getBody().getReferenceInputs() == null)
                transaction.getBody().setReferenceInputs(referenceInputs);
            else
                transaction.getBody().getReferenceInputs().addAll(referenceInputs);
        };
    }

}
